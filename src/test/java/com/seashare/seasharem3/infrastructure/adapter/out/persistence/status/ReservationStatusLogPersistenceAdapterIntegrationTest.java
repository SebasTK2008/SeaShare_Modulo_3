package com.seashare.seasharem3.infrastructure.adapter.out.persistence.status;

import com.seashare.seasharem3.TestcontainersConfiguration;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.FinancialOperationType;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.domain.valueobject.ReservationStatus;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.parameters.FinancialParametersPersistenceAdapter;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.reservation.ReservationInformationPersistenceAdapter;
import com.seashare.seasharem3.support.ChargeFixtures;
import com.seashare.seasharem3.support.IntegrationTestProperties;
import com.seashare.seasharem3.support.RetryProbeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integración de {@code reservation_status_log} contra PostgreSQL real: reclamo idempotente por la
 * clave de negocio, marca de procesado y FK de la reserva. Fuente: UC07 plan T007.
 */
@SpringBootTest(properties = {IntegrationTestProperties.OUTBOX_RELAY_INTERVAL,
        IntegrationTestProperties.RETRY_INITIAL_INTERVAL, IntegrationTestProperties.RETRY_MULTIPLIER,
        IntegrationTestProperties.RETRY_MAX_INTERVAL})
@Import({TestcontainersConfiguration.class, RetryProbeConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
class ReservationStatusLogPersistenceAdapterIntegrationTest {

    private static final Instant STATUS_CHANGED_AT = Instant.parse("2026-12-05T14:30:00Z");
    private static final Instant PROCESSED_AT = Instant.parse("2026-12-05T14:30:01Z");

    @Autowired
    private ReservationStatusLogPersistenceAdapter adapter;

    @Autowired
    private ReservationStatusLogJpaRepository repository;

    @Autowired
    private ReservationInformationPersistenceAdapter reservationAdapter;

    @Autowired
    private FinancialParametersPersistenceAdapter parametersAdapter;

    @Test
    void claimsTheOperationOnlyOnceAndMarksItProcessed() {
        ReservationId reservationId = createReservation();
        UUID rowId = UUID.randomUUID();
        IdempotencyKey key = new IdempotencyKey(reservationId.value() + ":COMPLETED");

        assertThat(adapter.insertIfAbsent(rowId, reservationId, ReservationStatus.COMPLETED,
                FinancialOperationType.SETTLEMENT, key, STATUS_CHANGED_AT)).isTrue();
        assertThat(adapter.insertIfAbsent(UUID.randomUUID(), reservationId, ReservationStatus.COMPLETED,
                FinancialOperationType.SETTLEMENT, key, STATUS_CHANGED_AT)).isFalse();

        assertThat(find(reservationId, ReservationStatus.COMPLETED, FinancialOperationType.SETTLEMENT, key))
                .hasValueSatisfying(stored -> {
                    assertThat(stored.getId()).isEqualTo(rowId);
                    assertThat(stored.getStatusChangedAt()).isEqualTo(STATUS_CHANGED_AT);
                    assertThat(stored.getProcessedAt()).isNull();
                    assertThat(stored.getOutcome()).isNull();
                });

        assertThat(adapter.markProcessed(rowId, PROCESSED_AT, "delegated")).isTrue();

        assertThat(find(reservationId, ReservationStatus.COMPLETED, FinancialOperationType.SETTLEMENT, key))
                .hasValueSatisfying(stored -> {
                    assertThat(stored.getProcessedAt()).isEqualTo(PROCESSED_AT);
                    assertThat(stored.getOutcome()).isEqualTo("delegated");
                });
    }

    @Test
    void rejectsAnOperationForAnUnknownReservation() {
        ReservationId unknown = new ReservationId(UUID.randomUUID());

        assertThatThrownBy(() -> adapter.insertIfAbsent(UUID.randomUUID(), unknown, ReservationStatus.PENDING,
                FinancialOperationType.CHARGE, new IdempotencyKey(unknown.value() + ":PENDING"), STATUS_CHANGED_AT))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private java.util.Optional<ReservationStatusLogJpaEntity> find(ReservationId reservationId,
            ReservationStatus status, FinancialOperationType operationType, IdempotencyKey key) {
        return repository.findByReservationIdAndStatusAndOperationTypeAndIdempotencyKey(reservationId.value(),
                status.name(), operationType.name(), key.value());
    }

    private ReservationId createReservation() {
        ReservationInformation info = ChargeFixtures.calculatedReservation();
        long version = parametersAdapter.findCurrent().map(parameters -> parameters.versionNumber() + 1).orElse(1L);
        FinancialParameters parameters = parametersAdapter.saveNewVersion(FinancialParameters.create(
                UUID.randomUUID(), version, new Percentage(new BigDecimal("20")), Money.ZERO, Percentage.ZERO,
                Percentage.ZERO, "test", ChargeFixtures.CREATED_AT));
        ReservationInformation saved = reservationAdapter.save(ReservationInformation.create(info.reservationId(),
                info.boatId(), info.baseRate(), parameters.id(), info.startDate(), info.endDate(),
                info.passengers(), info.ownerId(), info.maxCapacity()));
        return saved.reservationId();
    }
}
