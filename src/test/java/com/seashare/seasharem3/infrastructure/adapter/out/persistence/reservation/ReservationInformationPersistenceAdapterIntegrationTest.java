package com.seashare.seasharem3.infrastructure.adapter.out.persistence.reservation;

import com.seashare.seasharem3.TestcontainersConfiguration;
import com.seashare.seasharem3.domain.exception.ReservationInfoNotFoundException;
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
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integración del adaptador de {@code reservation_information} contra PostgreSQL real: upsert que
 * invalida los montos calculados (D-UC03-02), escritura conjunta de UC04 y claves foráneas a
 * {@code financial_parameters} (D-24/D-27). Requiere Docker; en local se omiten.
 */
@SpringBootTest(properties = {IntegrationTestProperties.OUTBOX_RELAY_INTERVAL,
        IntegrationTestProperties.RETRY_INITIAL_INTERVAL, IntegrationTestProperties.RETRY_MULTIPLIER,
        IntegrationTestProperties.RETRY_MAX_INTERVAL})
@Import({TestcontainersConfiguration.class, RetryProbeConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
class ReservationInformationPersistenceAdapterIntegrationTest {

    private static final Instant CALCULATED_AT = Instant.parse("2026-11-01T10:15:30Z");

    @Autowired
    private ReservationInformationPersistenceAdapter adapter;

    @Autowired
    private FinancialParametersPersistenceAdapter parametersAdapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void saveInsertsTheInformativeRowWithoutCalculatedAmounts() {
        UUID reservationId = UUID.randomUUID();
        ReservationInformation information = newReservation(reservationId, null);

        ReservationInformation saved = adapter.save(information);

        assertThat(saved.isCalculated()).isFalse();
        ReservationInformation found = adapter.findByReservationId(new ReservationId(reservationId)).orElseThrow();
        assertThat(found.boatId()).isEqualTo(information.boatId());
        assertThat(found.baseRate()).isEqualTo(information.baseRate());
        assertThat(found.startDate()).isEqualTo(information.startDate());
        assertThat(found.endDate()).isEqualTo(information.endDate());
        assertThat(found.passengers()).isEqualTo(information.passengers());
        assertThat(found.ownerId()).isEqualTo(information.ownerId());
        assertThat(found.maxCapacity()).isEqualTo(information.maxCapacity());
        assertThat(found.baseRateParametersId()).isNull();
        assertThat(found.isCalculated()).isFalse();
    }

    @Test
    void saveAgainInvalidatesTheCalculatedAmountsFrozenParametersAndCalculatedAt() {
        UUID reservationId = UUID.randomUUID();
        UUID parametersId = newParametersVersion();
        ReservationInformation information = newReservation(reservationId, parametersId);
        adapter.save(information);
        adapter.saveCalculatedAmounts(calculated(information, newParametersVersion()));
        assertThat(adapter.findByReservationId(new ReservationId(reservationId)).orElseThrow().isCalculated()).isTrue();

        ReservationInformation updated = ReservationInformation.create(new ReservationId(reservationId),
                new BoatId(UUID.randomUUID()), new BaseRate(new Money(new BigDecimal("200.5"))), parametersId,
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 3), 3, new OwnerId(UUID.randomUUID()), 8);

        assertThat(adapter.save(updated).isCalculated()).isFalse();

        ReservationInformation found = adapter.findByReservationId(new ReservationId(reservationId)).orElseThrow();
        assertThat(found.passengers()).isEqualTo(3);
        assertThat(found.baseRate()).isEqualTo(new BaseRate(new Money(new BigDecimal("200.5"))));
        assertThat(found.isCalculated()).isFalse();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM reservation_information
                WHERE reservation_id = ? AND rental_amount IS NULL AND insurance_amount IS NULL
                  AND deposit_amount IS NULL AND total_amount IS NULL
                  AND frozen_parameters_id IS NULL AND calculated_at IS NULL
                """, Long.class, reservationId)).isEqualTo(1L);
    }

    @Test
    void saveCalculatedAmountsWritesAmountsFrozenParametersAndCalculatedAtTogether() {
        UUID reservationId = UUID.randomUUID();
        UUID baseRateParametersId = newParametersVersion();
        UUID frozenParametersId = newParametersVersion();
        ReservationInformation information = newReservation(reservationId, baseRateParametersId);
        adapter.save(information);

        ReservationInformation saved = adapter.saveCalculatedAmounts(calculated(information, frozenParametersId));

        assertThat(saved.isCalculated()).isTrue();
        ReservationInformation found = adapter.findByReservationId(new ReservationId(reservationId)).orElseThrow();
        assertThat(found.rentalAmount()).isEqualTo(new Money(new BigDecimal("500000")));
        assertThat(found.insuranceAmount()).isEqualTo(new Money(new BigDecimal("10000")));
        assertThat(found.depositAmount()).isEqualTo(new Money(new BigDecimal("200000")));
        assertThat(found.totalAmount()).isEqualTo(new Money(new BigDecimal("710000")));
        assertThat(found.frozenParametersId()).isEqualTo(frozenParametersId);
        assertThat(found.calculatedAt()).isEqualTo(CALCULATED_AT);
        assertThat(found.baseRateParametersId()).isEqualTo(baseRateParametersId);
    }

    @Test
    void saveCalculatedAmountsWithoutRowThrowsReservationInfoNotFoundException() {
        ReservationInformation information = calculated(newReservation(UUID.randomUUID(), null), UUID.randomUUID());

        assertThatThrownBy(() -> adapter.saveCalculatedAmounts(information))
                .isInstanceOf(ReservationInfoNotFoundException.class)
                .hasMessageContaining(information.reservationId().value().toString());
    }

    @Test
    void saveAgainRewritesBaseRateParametersId() {
        UUID reservationId = UUID.randomUUID();
        UUID firstParametersId = newParametersVersion();
        UUID secondParametersId = newParametersVersion();
        adapter.save(newReservation(reservationId, firstParametersId));

        ReservationInformation updated = ReservationInformation.create(
                new ReservationId(reservationId), new BoatId(UUID.randomUUID()),
                new BaseRate(new Money(new BigDecimal("150.5"))), secondParametersId,
                LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 3), 2,
                new OwnerId(UUID.randomUUID()), 5);

        adapter.save(updated);

        assertThat(adapter.findByReservationId(new ReservationId(reservationId)).orElseThrow()
                .baseRateParametersId()).isEqualTo(secondParametersId);
    }

    @Test
    void saveWithMissingParametersKeepsThePreviousValidRow() {
        UUID reservationId = UUID.randomUUID();
        ReservationInformation valid = newReservation(reservationId, newParametersVersion());
        adapter.save(valid);

        ReservationInformation invalid = ReservationInformation.create(
                new ReservationId(reservationId), new BoatId(UUID.randomUUID()),
                new BaseRate(new Money(new BigDecimal("200.5"))), UUID.randomUUID(),
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 3), 3,
                new OwnerId(UUID.randomUUID()), 8);

        assertThatThrownBy(() -> adapter.save(invalid)).isInstanceOf(DataAccessException.class);

        assertThat(adapter.findByReservationId(new ReservationId(reservationId)).orElseThrow())
                .isEqualTo(valid);
    }

    private UUID newParametersVersion() {
        long nextVersion = parametersAdapter.findCurrent().map(FinancialParameters::versionNumber).orElse(0L) + 1;
        return parametersAdapter.saveNewVersion(FinancialParameters.create(UUID.randomUUID(), nextVersion,
                new Percentage(new BigDecimal("10")), new Money(new BigDecimal("5000")),
                new Percentage(new BigDecimal("15")), new Percentage(new BigDecimal("20")), "integration-test",
                Instant.parse("2026-10-01T12:00:00Z"))).id();
    }

    private static ReservationInformation newReservation(UUID reservationId, UUID baseRateParametersId) {
        return ReservationInformation.create(new ReservationId(reservationId), new BoatId(UUID.randomUUID()),
                new BaseRate(new Money(new BigDecimal("120.5"))), baseRateParametersId, LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 5), 2, new OwnerId(UUID.randomUUID()), 6);
    }

    private static ReservationInformation calculated(ReservationInformation information, UUID frozenParametersId) {
        return information.withCalculated(new ReservationValue(new Money(new BigDecimal("500000")),
                new Money(new BigDecimal("10000")), new Money(new BigDecimal("200000")),
                new Money(new BigDecimal("710000"))), frozenParametersId, CALCULATED_AT);
    }
}
