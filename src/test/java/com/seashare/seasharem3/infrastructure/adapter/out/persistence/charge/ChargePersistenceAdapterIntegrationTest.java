package com.seashare.seasharem3.infrastructure.adapter.out.persistence.charge;

import com.seashare.seasharem3.TestcontainersConfiguration;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.model.ChargeRecord;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.ChargeIntentStatus;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.PaymentMethodReference;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.domain.valueobject.ReservationValue;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.reservation.ReservationInformationPersistenceAdapter;
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
 * Integracion de los adaptadores de {@code charge_intent} y {@code charge_record} contra PostgreSQL
 * real (T010): clave foranea a {@code reservation_information}, {@code UNIQUE(idempotency_key)},
 * varias intenciones por reserva, {@code UNIQUE(charge_intent_id)} y los triggers anti-mutacion del
 * registro de auditoria. Requiere Docker; en local se omite.
 */
@SpringBootTest(properties = {IntegrationTestProperties.OUTBOX_RELAY_INTERVAL,
        IntegrationTestProperties.RETRY_INITIAL_INTERVAL, IntegrationTestProperties.RETRY_MULTIPLIER,
        IntegrationTestProperties.RETRY_MAX_INTERVAL})
@Import({TestcontainersConfiguration.class, RetryProbeConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
class ChargePersistenceAdapterIntegrationTest {

    private static final Money TOTAL = new Money(new BigDecimal("990000.00"));
    private static final Instant CREATED_AT = Instant.parse("2026-11-01T10:15:30Z");

    @Autowired
    private ChargeIntentPersistenceAdapter intentAdapter;

    @Autowired
    private ChargeRecordPersistenceAdapter recordAdapter;

    @Autowired
    private ReservationInformationPersistenceAdapter reservationAdapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID registerReservation() {
        UUID reservationId = UUID.randomUUID();
        reservationAdapter.save(ReservationInformation.create(new ReservationId(reservationId),
                new BoatId(UUID.randomUUID()), new BaseRate(new Money(new BigDecimal("350000.00"))), null,
                LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 12), 2, new OwnerId(UUID.randomUUID()), 4));
        return reservationId;
    }

    /** La reserva debe tener el valor calculado por UC04: el registro de auditoria lo exige. */
    private ReservationInformation calculatedReservation(UUID reservationId) {
        ReservationInformation information = reservationOf(reservationId);
        return reservationAdapter.saveCalculatedAmounts(information.withCalculated(
                new ReservationValue(new Money(new BigDecimal("900000.00")), new Money(new BigDecimal("60000.00")),
                        new Money(new BigDecimal("30000.00")), TOTAL),
                UUID.randomUUID(), CREATED_AT));
    }

    private ReservationInformation reservationOf(UUID reservationId) {
        return reservationAdapter.findByReservationId(new ReservationId(reservationId)).orElseThrow();
    }

    private ChargeIntent intent(UUID reservationId, String idempotencyKey) {
        return ChargeIntent.create(UUID.randomUUID(), new ReservationId(reservationId),
                new IdempotencyKey(idempotencyKey), TOTAL,
                new PaymentMethodReference("token-123", "CREDIT_CARD", "4242"), CREATED_AT);
    }

    @Test
    void savesTheIntentAndReadsItBackByIdempotencyKey() {
        UUID reservationId = registerReservation();

        ChargeIntent saved = intentAdapter.save(intent(reservationId, "key-" + UUID.randomUUID()));

        assertThat(intentAdapter.findByIdempotencyKey(saved.idempotencyKey()))
                .get()
                .satisfies(found -> {
                    assertThat(found.id()).isEqualTo(saved.id());
                    assertThat(found.status()).isEqualTo(ChargeIntentStatus.PENDING_SEND);
                    assertThat(found.amount()).isEqualTo(TOTAL);
                    assertThat(found.paymentMethod().lastFour()).isEqualTo("4242");
                });
    }

    @Test
    void rejectsASecondIntentWithTheSameIdempotencyKey() {
        UUID reservationId = registerReservation();
        String idempotencyKey = "key-" + UUID.randomUUID();
        intentAdapter.save(intent(reservationId, idempotencyKey));

        assertThatThrownBy(() -> intentAdapter.save(intent(reservationId, idempotencyKey)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void allowsSeveralIntentsForTheSameReservation() {
        UUID reservationId = registerReservation();

        ChargeIntent first = intentAdapter.save(intent(reservationId, "key-" + UUID.randomUUID()));
        ChargeIntent second = intentAdapter.save(intent(reservationId, "key-" + UUID.randomUUID()));

        assertThat(first.reservationId()).isEqualTo(second.reservationId());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM charge_intent WHERE reservation_id = ?", Integer.class, reservationId))
                .isEqualTo(2);
    }

    @Test
    void rejectsAnIntentOfAnUnknownReservation() {
        assertThatThrownBy(() -> intentAdapter.save(intent(UUID.randomUUID(), "key-" + UUID.randomUUID())))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void savesTheAuditRecordAndRejectsItsMutation() {
        UUID reservationId = registerReservation();
        calculatedReservation(reservationId);
        ChargeIntent saved = intentAdapter.save(intent(reservationId, "key-" + UUID.randomUUID()));
        ChargeIntent captured = saved.transitionTo(ChargeIntentStatus.IN_PROCESS)
                .transitionTo(ChargeIntentStatus.CAPTURED);

        ChargeRecord stored = recordAdapter.save(
                ChargeRecord.create(UUID.randomUUID(), captured, reservationOf(reservationId), CREATED_AT));

        assertThat(stored.amount()).isEqualTo(TOTAL);
        assertThat(stored.chargeIntentId()).isEqualTo(saved.id());
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE charge_record SET amount = 1 WHERE id = ?",
                stored.id()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void rejectsASecondAuditRecordForTheSameIntent() {
        UUID reservationId = registerReservation();
        calculatedReservation(reservationId);
        ChargeIntent saved = intentAdapter.save(intent(reservationId, "key-" + UUID.randomUUID()));
        ChargeIntent captured = saved.transitionTo(ChargeIntentStatus.IN_PROCESS)
                .transitionTo(ChargeIntentStatus.CAPTURED);
        recordAdapter.save(ChargeRecord.create(UUID.randomUUID(), captured, reservationOf(reservationId), CREATED_AT));

        assertThatThrownBy(() -> recordAdapter.save(
                ChargeRecord.create(UUID.randomUUID(), captured, reservationOf(reservationId), CREATED_AT)))
                .isInstanceOf(DataAccessException.class);
    }
}
