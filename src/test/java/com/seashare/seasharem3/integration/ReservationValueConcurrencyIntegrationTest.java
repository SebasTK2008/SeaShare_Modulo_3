package com.seashare.seasharem3.integration;

import com.seashare.seasharem3.TestcontainersConfiguration;
import com.seashare.seasharem3.application.dto.BaseRateResult;
import com.seashare.seasharem3.application.dto.CalculateReservationValueCommand;
import com.seashare.seasharem3.application.dto.ProvideBaseRateCommand;
import com.seashare.seasharem3.application.dto.RegisterReservationInformationCommand;
import com.seashare.seasharem3.application.dto.ReservationValueResult;
import com.seashare.seasharem3.application.port.in.ProvideBaseRateUseCase;
import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.application.port.out.ReservationInformationRepository;
import com.seashare.seasharem3.application.service.CalculateReservationValueService;
import com.seashare.seasharem3.application.service.RegisterReservationInformationService;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.service.ReservationValueCalculator;
import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.lock.PostgresReservationLockAdapter;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.parameters.FinancialParametersPersistenceAdapter;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.reservation.ReservationInformationPersistenceAdapter;
import com.seashare.seasharem3.support.IntegrationTestProperties;
import com.seashare.seasharem3.support.RetryProbeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrencia real de UC04 sobre PostgreSQL (T019): el cálculo de UC04 toma el bloqueo asesor antes
 * de leer, de modo que el <em>upsert</em> de UC03 se serializa con él y no puede invalidar la fila
 * entre la lectura y la escritura (general-plan D-15; regla 18 del plan de UC04).
 *
 * <p>La transacción la aporta el {@code TransactionTemplate} porque el servicio se construye a mano
 * para poder interponer el repositorio que sincroniza ambas carreras. Requiere Docker; sin Docker se
 * omite.</p>
 */
@SpringBootTest(properties = {IntegrationTestProperties.OUTBOX_RELAY_INTERVAL,
        IntegrationTestProperties.RETRY_INITIAL_INTERVAL, IntegrationTestProperties.RETRY_MULTIPLIER,
        IntegrationTestProperties.RETRY_MAX_INTERVAL})
@Import({TestcontainersConfiguration.class, RetryProbeConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
class ReservationValueConcurrencyIntegrationTest {

    private static final UUID RESERVATION_UUID = UUID.randomUUID();
    private static final ReservationId RESERVATION_ID = new ReservationId(RESERVATION_UUID);
    private static final BoatId BOAT = new BoatId(UUID.fromString("3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01"));
    private static final OwnerId OWNER = new OwnerId(UUID.fromString("a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d"));
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-11-01T10:15:30Z"), ZoneOffset.UTC);

    @Autowired
    private ReservationInformationPersistenceAdapter reservationAdapter;

    @Autowired
    private FinancialParametersPersistenceAdapter parametersAdapter;

    @Autowired
    private PostgresReservationLockAdapter lockAdapter;

    @Autowired
    private FailureRecorderPort failureRecorder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void upsertAndCalculationAreSerializedByTheAdvisoryLock() throws Exception {
        // Versión vigente que UC04 usará en el primer cálculo (sin frozen_parameters_id en la fila).
        parametersAdapter.saveNewVersion(FinancialParameters.create(
                UUID.randomUUID(), nextVersion(), new Percentage(new BigDecimal("20.00")),
                new Money(new BigDecimal("15000.00")), Percentage.ZERO, Percentage.ZERO,
                "integration-test", Instant.parse("2026-10-01T12:00:00Z")));
        reservationAdapter.save(originalReservation());

        CountDownLatch readingWhileHoldingLock = new CountDownLatch(1);
        CountDownLatch releaseCalculation = new CountDownLatch(1);
        CalculateReservationValueService uc04 = new CalculateReservationValueService(
                new LatchingRepository(reservationAdapter, readingWhileHoldingLock, releaseCalculation),
                parametersAdapter, failureRecorder, lockAdapter, new ReservationValueCalculator(), FIXED_CLOCK);
        RegisterReservationInformationService uc03 =
                new RegisterReservationInformationService(new FixedBaseRateUseCase(), reservationAdapter);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ReservationValueResult> calculation = executor.submit(
                    () -> transactions.execute(status -> uc04.calculate(new CalculateReservationValueCommand(
                            RESERVATION_ID))));
            assertThat(readingWhileHoldingLock.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> upsert = executor.submit(() -> uc03.register(reRegistration()));
            // UC04 retiene el bloqueo: el upsert de UC03 no puede invalidar la fila mientras se calcula.
            Thread.sleep(500);
            assertThat(upsert.isDone()).isFalse();

            releaseCalculation.countDown();
            ReservationValueResult result = calculation.get(10, TimeUnit.SECONDS);
            upsert.get(10, TimeUnit.SECONDS);

            // Sin pérdida de escritura: UC04 terminó con un desglose coherente…
            assertThat(result).isNotNull();
            assertThat(result.totalAmount()).isEqualTo(result.rentalAmount()
                    .add(result.insuranceAmount()).add(result.guaranteeDepositAmount()));

            // …y la invalidación de UC03 fue completa y posterior (nunca se mezclan datos de ambas).
            ReservationInformation finalRow = reservationAdapter.findByReservationId(RESERVATION_ID).orElseThrow();
            assertThat(finalRow.startDate()).isEqualTo(LocalDate.of(2027, 1, 1));
            assertThat(finalRow.isCalculated()).isFalse();
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT count(*) FROM reservation_information
                    WHERE reservation_id = ? AND rental_amount IS NULL AND insurance_amount IS NULL
                      AND deposit_amount IS NULL AND total_amount IS NULL
                      AND frozen_parameters_id IS NULL AND calculated_at IS NULL
                    """, Long.class, RESERVATION_UUID)).isEqualTo(1L);
        } finally {
            releaseCalculation.countDown();
            executor.shutdownNow();
        }
    }

    private long nextVersion() {
        return parametersAdapter.findCurrent().map(FinancialParameters::versionNumber).orElse(0L) + 1;
    }

    private static ReservationInformation originalReservation() {
        return ReservationInformation.create(RESERVATION_ID, BOAT,
                new BaseRate(new Money(new BigDecimal("300000.00"))), null,
                LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 12), 4, OWNER, 60);
    }

    private static RegisterReservationInformationCommand reRegistration() {
        return new RegisterReservationInformationCommand(RESERVATION_ID, BOAT,
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 3), 3, OWNER, 60);
    }

    /** Repositorio que pausa la lectura de UC04 mientras retiene el bloqueo asesor de su transacción. */
    private static final class LatchingRepository implements ReservationInformationRepository {

        private final ReservationInformationRepository delegate;
        private final CountDownLatch reading;
        private final CountDownLatch release;

        private LatchingRepository(ReservationInformationRepository delegate, CountDownLatch reading,
                CountDownLatch release) {
            this.delegate = delegate;
            this.reading = reading;
            this.release = release;
        }

        @Override
        public Optional<ReservationInformation> findByReservationId(ReservationId reservationId) {
            reading.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Calculation was interrupted while waiting", exception);
            }
            return delegate.findByReservationId(reservationId);
        }

        @Override
        public ReservationInformation save(ReservationInformation information) {
            return delegate.save(information);
        }

        @Override
        public ReservationInformation saveCalculatedAmounts(ReservationInformation information) {
            return delegate.saveCalculatedAmounts(information);
        }
    }

    /** Devuelve siempre la misma tarifa para que UC03 no dependa de Flota ni de UC02. */
    private static final class FixedBaseRateUseCase implements ProvideBaseRateUseCase {

        @Override
        public List<BaseRateResult> provideBaseRates(ProvideBaseRateCommand command) {
            return List.of(new BaseRateResult(BOAT, new BaseRate(new Money(new BigDecimal("250000.00"))), null));
        }
    }
}
