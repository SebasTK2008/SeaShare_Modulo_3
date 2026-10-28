package com.seashare.seasharem3.integration;

import com.seashare.seasharem3.TestcontainersConfiguration;
import com.seashare.seasharem3.application.dto.BaseRateResult;
import com.seashare.seasharem3.application.dto.ProvideBaseRateCommand;
import com.seashare.seasharem3.application.dto.RegisterReservationInformationCommand;
import com.seashare.seasharem3.application.port.in.ProvideBaseRateUseCase;
import com.seashare.seasharem3.application.port.in.RegisterReservationInformationUseCase;
import com.seashare.seasharem3.application.port.out.ReservationInformationRepository;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.domain.valueobject.ReservationValue;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.parameters.FinancialParametersPersistenceAdapter;
import com.seashare.seasharem3.support.IntegrationTestProperties;
import com.seashare.seasharem3.support.RetryProbeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de aceptación CE-001 de UC03 contra PostgreSQL real: la reserva notificada queda registrada
 * completa con la tarifa base exacta y un segundo mensaje para la misma reserva sobrescribe el bloque
 * informativo e invalida los montos calculados (D-UC03-02), con cero discrepancias.
 *
 * <p>UC02 se sustituye por un doble para aislar el adaptador de persistencia; la inclusión real se
 * cubre en {@code ReservationInformationSourceIntegrityAcceptanceTest}.</p>
 */
@SpringBootTest(properties = {IntegrationTestProperties.OUTBOX_RELAY_INTERVAL,
        IntegrationTestProperties.RETRY_INITIAL_INTERVAL, IntegrationTestProperties.RETRY_MULTIPLIER,
        IntegrationTestProperties.RETRY_MAX_INTERVAL})
@Import({TestcontainersConfiguration.class, RetryProbeConfiguration.class,
        ReservationInformationPersistenceAcceptanceTest.StubUc02Configuration.class})
@Testcontainers(disabledWithoutDocker = true)
class ReservationInformationPersistenceAcceptanceTest {

    private static final UUID RESERVATION_UUID = UUID.fromString("b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10");
    private static final UUID BOAT_UUID = UUID.fromString("3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01");
    private static final UUID OWNER_UUID = UUID.fromString("a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d");
    private static final ReservationId RESERVATION_ID = new ReservationId(RESERVATION_UUID);
    private static final BoatId BOAT = new BoatId(BOAT_UUID);
    private static final OwnerId OWNER = new OwnerId(OWNER_UUID);
    private static final LocalDate START = LocalDate.of(2026, 12, 20);
    private static final LocalDate END = LocalDate.of(2026, 12, 22);
    private static final BaseRate BASE_RATE = new BaseRate(new Money(new BigDecimal("437500.0000")));
    private static final BaseRate NEW_BASE_RATE = new BaseRate(new Money(new BigDecimal("400000.0000")));
    private static final Instant CALCULATED_AT = Instant.parse("2026-11-01T10:15:30Z");

    @Autowired
    private RegisterReservationInformationUseCase useCase;

    @Autowired
    private ReservationInformationRepository repository;

    @Autowired
    private FinancialParametersPersistenceAdapter parametersAdapter;

    @Autowired
    private StubProvideBaseRateUseCase uc02;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ce001_reserva_notificada_registrada_completa_y_upsert() {
        uc02.respondWith(new BaseRateResult(BOAT, BASE_RATE, null));

        useCase.register(command(START, END, 4, 6));

        ReservationInformation saved = repository.findByReservationId(RESERVATION_ID).orElseThrow();
        assertThat(saved.reservationId()).isEqualTo(RESERVATION_ID);
        assertThat(saved.boatId()).isEqualTo(BOAT);
        assertThat(saved.baseRate()).isEqualTo(BASE_RATE);
        assertThat(saved.baseRate().amount().amount()).isEqualByComparingTo("437500.0000");
        assertThat(saved.baseRate().amount().amount().scale()).isEqualTo(4);
        assertThat(saved.startDate()).isEqualTo(START);
        assertThat(saved.endDate()).isEqualTo(END);
        assertThat(saved.passengers()).isEqualTo(4);
        assertThat(saved.ownerId()).isEqualTo(OWNER);
        assertThat(saved.maxCapacity()).isEqualTo(6);
        assertThat(saved.baseRateParametersId()).isNull();
        assertThat(saved.isCalculated()).isFalse();

        ReservationValue value = new ReservationValue(
                new Money(new BigDecimal("900000")),
                new Money(new BigDecimal("60000")),
                new Money(new BigDecimal("30000")),
                new Money(new BigDecimal("990000")));
        UUID frozenParametersId = newParametersVersion();
        repository.saveCalculatedAmounts(saved.withCalculated(value, frozenParametersId, CALCULATED_AT));
        assertThat(repository.findByReservationId(RESERVATION_ID).orElseThrow().isCalculated()).isTrue();

        uc02.respondWith(new BaseRateResult(BOAT, NEW_BASE_RATE, null));
        useCase.register(command(START, END.plusDays(1), 3, 8));

        ReservationInformation updated = repository.findByReservationId(RESERVATION_ID).orElseThrow();
        assertThat(updated.boatId()).isEqualTo(BOAT);
        assertThat(updated.baseRate()).isEqualTo(NEW_BASE_RATE);
        assertThat(updated.startDate()).isEqualTo(START);
        assertThat(updated.endDate()).isEqualTo(END.plusDays(1));
        assertThat(updated.passengers()).isEqualTo(3);
        assertThat(updated.ownerId()).isEqualTo(OWNER);
        assertThat(updated.maxCapacity()).isEqualTo(8);
        assertThat(updated.isCalculated()).isFalse();
        assertThat(updated.rentalAmount()).isNull();
        assertThat(updated.insuranceAmount()).isNull();
        assertThat(updated.depositAmount()).isNull();
        assertThat(updated.totalAmount()).isNull();
        assertThat(updated.frozenParametersId()).isNull();
        assertThat(updated.calculatedAt()).isNull();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM reservation_information
                WHERE reservation_id = ? AND rental_amount IS NULL AND insurance_amount IS NULL
                  AND deposit_amount IS NULL AND total_amount IS NULL
                  AND frozen_parameters_id IS NULL AND calculated_at IS NULL
                """, Long.class, RESERVATION_UUID)).isEqualTo(1L);
    }

    private UUID newParametersVersion() {
        long nextVersion = parametersAdapter.findCurrent()
                .map(FinancialParameters::versionNumber)
                .orElse(0L) + 1;
        return parametersAdapter.saveNewVersion(FinancialParameters.create(
                UUID.randomUUID(),
                nextVersion,
                new Percentage(new BigDecimal("10")),
                new Money(new BigDecimal("5000")),
                new Percentage(new BigDecimal("15")),
                new Percentage(new BigDecimal("20")),
                "integration-test",
                CALCULATED_AT)).id();
    }

    private static RegisterReservationInformationCommand command(LocalDate startDate, LocalDate endDate,
            int passengers, int maxCapacity) {
        return new RegisterReservationInformationCommand(RESERVATION_ID, BOAT, startDate, endDate,
                passengers, OWNER, maxCapacity);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StubUc02Configuration {

        @Bean
        @Primary
        StubProvideBaseRateUseCase stubProvideBaseRateUseCase() {
            return new StubProvideBaseRateUseCase();
        }
    }

    static final class StubProvideBaseRateUseCase implements ProvideBaseRateUseCase {

        private Supplier<List<BaseRateResult>> response = () -> List.of();

        @Override
        public List<BaseRateResult> provideBaseRates(ProvideBaseRateCommand command) {
            return response.get();
        }

        void respondWith(BaseRateResult result) {
            response = () -> List.of(result);
        }
    }
}
