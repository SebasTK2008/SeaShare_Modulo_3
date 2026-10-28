package com.seashare.seasharem3.integration;

import com.seashare.seasharem3.TestcontainersConfiguration;
import com.seashare.seasharem3.application.dto.BaseRateResult;
import com.seashare.seasharem3.application.dto.CalculateReservationValueCommand;
import com.seashare.seasharem3.application.dto.ProvideBaseRateCommand;
import com.seashare.seasharem3.application.dto.RegisterReservationInformationCommand;
import com.seashare.seasharem3.application.dto.ReservationValueResult;
import com.seashare.seasharem3.application.port.in.ProvideBaseRateUseCase;
import com.seashare.seasharem3.application.service.CalculateReservationValueService;
import com.seashare.seasharem3.application.service.RegisterReservationInformationService;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.parameters.FinancialParametersPersistenceAdapter;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.reservation.ReservationInformationPersistenceAdapter;
import com.seashare.seasharem3.support.IntegrationTestProperties;
import com.seashare.seasharem3.support.RetryProbeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aceptación de UC04 contra PostgreSQL real (T015, T016, CE-004): el cálculo completo deja la fila
 * con los cuatro montos, la referencia de versión y {@code calculated_at}; una versión nueva no
 * altera lo resuelto (D-17) y el <em>upsert</em> de UC03 invalida todo para que el siguiente cálculo
 * lo regenere. Requiere Docker; sin Docker se omite.
 */
@SpringBootTest(properties = {IntegrationTestProperties.OUTBOX_RELAY_INTERVAL,
        IntegrationTestProperties.RETRY_INITIAL_INTERVAL, IntegrationTestProperties.RETRY_MULTIPLIER,
        IntegrationTestProperties.RETRY_MAX_INTERVAL})
@Import({TestcontainersConfiguration.class, RetryProbeConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
class ReservationValuePersistenceAcceptanceTest {

    private static final BoatId BOAT = new BoatId(UUID.fromString("3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01"));
    private static final OwnerId OWNER = new OwnerId(UUID.fromString("a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d"));

    @Autowired
    private ReservationInformationPersistenceAdapter reservationAdapter;

    @Autowired
    private FinancialParametersPersistenceAdapter parametersAdapter;

    @Autowired
    private CalculateReservationValueService uc04;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ce004_breakdownIsPersistedWithTheReferencedVersionForTheCharge() {
        UUID reservationId = UUID.randomUUID();
        UUID parametersId = newParametersVersion("15000.00");
        reservationAdapter.save(registration(reservationId, LocalDate.of(2026, 12, 10),
                LocalDate.of(2026, 12, 12), 4));

        ReservationValueResult result = uc04.calculate(new CalculateReservationValueCommand(
                new ReservationId(reservationId)));

        assertThat(result.totalAmount().amount()).isEqualByComparingTo("990000.00");
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT rental_amount, insurance_amount, deposit_amount, total_amount, "
                        + "frozen_parameters_id, calculated_at FROM reservation_information WHERE reservation_id = ?",
                reservationId);
        assertThat((BigDecimal) row.get("rental_amount")).isEqualByComparingTo("900000.00");
        assertThat((BigDecimal) row.get("insurance_amount")).isEqualByComparingTo("60000.00");
        assertThat((BigDecimal) row.get("deposit_amount")).isEqualByComparingTo("30000.00");
        assertThat((BigDecimal) row.get("total_amount")).isEqualByComparingTo("990000.00");
        assertThat(row.get("frozen_parameters_id")).isEqualTo(parametersId);
        assertThat(row.get("calculated_at")).isNotNull();
    }

    @Test
    void recalculationKeepsTheReferencedVersionWhenANewVersionIsPublished() {
        UUID reservationId = UUID.randomUUID();
        UUID firstVersion = newParametersVersion("15000.00");
        reservationAdapter.save(registration(reservationId, LocalDate.of(2026, 12, 10),
                LocalDate.of(2026, 12, 12), 4));
        ReservationValueResult first = uc04.calculate(new CalculateReservationValueCommand(
                new ReservationId(reservationId)));

        // El Administrador Financiero publica una versión nueva: no cambia nada para una reserva ya calculada.
        newParametersVersion("99999.00");
        ReservationValueResult second = uc04.calculate(new CalculateReservationValueCommand(
                new ReservationId(reservationId)));

        assertThat(second).isEqualTo(first);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT frozen_parameters_id FROM reservation_information WHERE reservation_id = ?",
                UUID.class, reservationId)).isEqualTo(firstVersion);
    }

    @Test
    void upsertOfUc03InvalidatesAndTheNextCalculationRebuildsTheBreakdown() {
        UUID reservationId = UUID.randomUUID();
        newParametersVersion("15000.00");
        reservationAdapter.save(registration(reservationId, LocalDate.of(2026, 12, 10),
                LocalDate.of(2026, 12, 12), 4));
        uc04.calculate(new CalculateReservationValueCommand(new ReservationId(reservationId)));
        assertThat(countInvalidatedRows(reservationId)).isEqualTo(0L);

        // UC03 vuelve a registrar la reserva con otras fechas y otra tarifa: todo lo calculado queda en NULL.
        new RegisterReservationInformationService(new FixedBaseRateUseCase(), reservationAdapter)
                .register(new RegisterReservationInformationCommand(new ReservationId(reservationId), BOAT,
                        LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 3), 3, OWNER, 60));
        assertThat(countInvalidatedRows(reservationId)).isEqualTo(1L);

        ReservationValueResult recalculated = uc04.calculate(new CalculateReservationValueCommand(
                new ReservationId(reservationId)));

        assertThat(recalculated.rentalAmount().amount()).isEqualByComparingTo("750000.00");
        assertThat(recalculated.insuranceAmount().amount()).isEqualByComparingTo("45000.00");
        assertThat(recalculated.guaranteeDepositAmount().amount()).isEqualByComparingTo("25000.00");
        assertThat(recalculated.totalAmount().amount()).isEqualByComparingTo("820000.00");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT start_date FROM reservation_information WHERE reservation_id = ?",
                LocalDate.class, reservationId)).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    private long countInvalidatedRows(UUID reservationId) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM reservation_information
                WHERE reservation_id = ? AND rental_amount IS NULL AND insurance_amount IS NULL
                  AND deposit_amount IS NULL AND total_amount IS NULL
                  AND frozen_parameters_id IS NULL AND calculated_at IS NULL
                """, Long.class, reservationId);
    }

    private UUID newParametersVersion(String insuranceFeePerPassenger) {
        long nextVersion = parametersAdapter.findCurrent().map(FinancialParameters::versionNumber).orElse(0L) + 1;
        return parametersAdapter.saveNewVersion(FinancialParameters.create(UUID.randomUUID(), nextVersion,
                new Percentage(new BigDecimal("20.00")), new Money(new BigDecimal(insuranceFeePerPassenger)),
                Percentage.ZERO, Percentage.ZERO, "integration-test",
                Instant.parse("2026-10-01T12:00:00Z"))).id();
    }

    private static ReservationInformation registration(UUID reservationId, LocalDate start, LocalDate end,
            int passengers) {
        return ReservationInformation.create(new ReservationId(reservationId), BOAT,
                new BaseRate(new Money(new BigDecimal("300000.00"))), null, start, end, passengers, OWNER, 60);
    }

    /** Devuelve una tarifa fija para que el re-registro de UC03 no dependa de Flota ni de UC02. */
    private static final class FixedBaseRateUseCase implements ProvideBaseRateUseCase {

        @Override
        public List<BaseRateResult> provideBaseRates(ProvideBaseRateCommand command) {
            return List.of(new BaseRateResult(BOAT, new BaseRate(new Money(new BigDecimal("250000.00"))), null));
        }
    }
}
