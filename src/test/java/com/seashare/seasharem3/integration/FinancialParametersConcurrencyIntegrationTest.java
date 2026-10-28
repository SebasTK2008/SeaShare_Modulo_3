package com.seashare.seasharem3.integration;

import com.seashare.seasharem3.TestcontainersConfiguration;
import com.seashare.seasharem3.application.dto.FinancialParametersCommand;
import com.seashare.seasharem3.application.dto.SaveFinancialParametersResult;
import com.seashare.seasharem3.application.exception.ContractException;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.out.FinancialParametersRepository;
import com.seashare.seasharem3.application.service.FinancialParametersService;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.service.HighSeasonCalendar;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.parameters.FinancialParametersJpaRepository;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.parameters.FinancialParametersPersistenceAdapter;
import com.seashare.seasharem3.support.FixedClockPort;
import com.seashare.seasharem3.support.IntegrationTestProperties;
import com.seashare.seasharem3.support.RetryProbeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrencia real de UC11 sobre PostgreSQL: dos guardados que leen la misma versión vigente
 * compiten por {@code UNIQUE(version_number)}; uno gana y el otro conserva la causa técnica.
 * Requiere Docker; sin Docker se omite.
 */
@SpringBootTest(properties = {IntegrationTestProperties.OUTBOX_RELAY_INTERVAL,
        IntegrationTestProperties.RETRY_INITIAL_INTERVAL, IntegrationTestProperties.RETRY_MULTIPLIER,
        IntegrationTestProperties.RETRY_MAX_INTERVAL})
@Import({TestcontainersConfiguration.class, RetryProbeConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
class FinancialParametersConcurrencyIntegrationTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-10-24T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 10, 24);

    @Autowired
    private FinancialParametersPersistenceAdapter adapter;

    @Autowired
    private FinancialParametersJpaRepository jpaRepository;

    @Test
    void twoConcurrentSavesWithSameBaseVersionCreateOnlyOneVersion() throws Exception {
        long baseline = currentVersion();
        long baselineCount = jpaRepository.count();

        CyclicBarrier barrier = new CyclicBarrier(2);
        FinancialParametersService service = serviceWith(new BarrierRepository(adapter, barrier));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<SaveFinancialParametersResult> first = executor.submit(
                    () -> service.save(command("11.00")));
            Future<SaveFinancialParametersResult> second = executor.submit(
                    () -> service.save(command("12.00")));

            List<Object> outcomes = new ArrayList<>();
            for (Future<SaveFinancialParametersResult> future : List.of(first, second)) {
                try {
                    outcomes.add(future.get(10, TimeUnit.SECONDS));
                } catch (ExecutionException exception) {
                    outcomes.add(exception.getCause());
                }
            }

            List<SaveFinancialParametersResult> successes = outcomes.stream()
                    .filter(SaveFinancialParametersResult.class::isInstance)
                    .map(SaveFinancialParametersResult.class::cast)
                    .toList();
            List<ContractException> failures = outcomes.stream()
                    .filter(ContractException.class::isInstance)
                    .map(ContractException.class::cast)
                    .toList();

            assertThat(successes).hasSize(1);
            assertThat(successes.getFirst().versionNumber()).isEqualTo(baseline + 1);
            assertThat(failures).hasSize(1);
            assertThat(failures.getFirst().code()).isEqualTo(ErrorCode.PARAMETERS_SAVE_FAILED);
            assertThat(failures.getFirst()).hasCauseInstanceOf(DataIntegrityViolationException.class);

            assertThat(jpaRepository.count()).isEqualTo(baselineCount + 1);
            FinancialParameters committed = adapter.findCurrent().orElseThrow();
            assertThat(committed.versionNumber()).isEqualTo(baseline + 1);
            assertThat(committed.commissionPct().value())
                    .isIn(new BigDecimal("11.0000"), new BigDecimal("12.0000"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void retryAfterConflictCreatesNextVersion() {
        long baseline = currentVersion();

        BarrierRepository neverBlocks = new BarrierRepository(adapter, new CyclicBarrier(1));
        FinancialParametersService service = serviceWith(neverBlocks);

        SaveFinancialParametersResult result = service.save(command("13.00"));

        assertThat(result.versionNumber()).isEqualTo(baseline + 1);
        assertThat(adapter.findCurrent().orElseThrow().commissionPct().value())
                .isEqualByComparingTo("13.0000");
    }

    private FinancialParametersService serviceWith(FinancialParametersRepository repository) {
        return new FinancialParametersService(repository, new HighSeasonCalendar(),
                new FixedClockPort(FIXED_DATE), FIXED_CLOCK);
    }

    private long currentVersion() {
        return adapter.findCurrent().map(FinancialParameters::versionNumber).orElse(0L);
    }

    private static FinancialParametersCommand command(String commission) {
        return new FinancialParametersCommand(
                new Percentage(new BigDecimal(commission)),
                new Money(new BigDecimal("5000.00")),
                new Percentage(new BigDecimal("10.00")),
                new Percentage(new BigDecimal("15.00")));
    }

    /**
     * Decorador que fuerza a los dos hilos a leer la misma versión vigente antes de insertar.
     * Así la carrera sobre {@code UNIQUE(version_number)} es determinista.
     */
    private static final class BarrierRepository implements FinancialParametersRepository {

        private final FinancialParametersRepository delegate;
        private final CyclicBarrier barrier;

        private BarrierRepository(FinancialParametersRepository delegate, CyclicBarrier barrier) {
            this.delegate = delegate;
            this.barrier = barrier;
        }

        @Override
        public Optional<FinancialParameters> findCurrent() {
            Optional<FinancialParameters> current = delegate.findCurrent();
            try {
                barrier.await(5, TimeUnit.SECONDS);
            } catch (Exception exception) {
                throw new IllegalStateException("Concurrent barrier failed", exception);
            }
            return current;
        }

        @Override
        public Optional<FinancialParameters> findById(UUID parametersId) {
            return delegate.findById(parametersId);
        }

        @Override
        public FinancialParameters saveNewVersion(FinancialParameters params) {
            return delegate.saveNewVersion(params);
        }
    }
}
