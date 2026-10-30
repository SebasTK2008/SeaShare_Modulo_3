package com.seashare.seasharem3.integration;

import com.seashare.seasharem3.TestcontainersConfiguration;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.PaymentMethodReference;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.charge.ChargeIntentPersistenceAdapter;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.reservation.ReservationInformationPersistenceAdapter;
import com.seashare.seasharem3.support.IntegrationTestProperties;
import com.seashare.seasharem3.support.RetryProbeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integración de la consulta de la intención más reciente contra PostgreSQL real: orden por
 * {@code created_at} y desempate por {@code id} descendente. Fuente: UC06 plan T010 y D-UC06-05.
 */
@SpringBootTest(properties = {IntegrationTestProperties.OUTBOX_RELAY_INTERVAL,
        IntegrationTestProperties.RETRY_INITIAL_INTERVAL, IntegrationTestProperties.RETRY_MULTIPLIER,
        IntegrationTestProperties.RETRY_MAX_INTERVAL})
@Import({TestcontainersConfiguration.class, RetryProbeConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
class PaymentConfirmationLatestIntentIntegrationTest {

    private static final Money TOTAL = new Money(new BigDecimal("990000.00"));

    @Autowired
    private ChargeIntentPersistenceAdapter intentAdapter;

    @Autowired
    private ReservationInformationPersistenceAdapter reservationAdapter;

    @Test
    void returnsTheMostRecentIntentByCreatedAt() {
        UUID reservationId = registerReservation();
        ChargeIntent older = intentAdapter.save(intent(reservationId, UUID.randomUUID(),
                Instant.parse("2026-10-20T15:00:00Z")));
        ChargeIntent newer = intentAdapter.save(intent(reservationId, UUID.randomUUID(),
                Instant.parse("2026-10-20T16:00:00Z")));

        ChargeIntent latest = intentAdapter.findLatestByReservationId(new ReservationId(reservationId))
                .orElseThrow();

        assertThat(latest.id()).isEqualTo(newer.id());
        assertThat(latest.id()).isNotEqualTo(older.id());
    }

    @Test
    void breaksTiesByIntentIdDescending() {
        UUID reservationId = registerReservation();
        UUID lowerId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID higherId = UUID.fromString("00000000-0000-4000-8000-000000000002");
        Instant sameCreatedAt = Instant.parse("2026-10-20T15:00:00Z");

        intentAdapter.save(intent(reservationId, higherId, sameCreatedAt));
        intentAdapter.save(intent(reservationId, lowerId, sameCreatedAt));

        ChargeIntent latest = intentAdapter.findLatestByReservationId(new ReservationId(reservationId))
                .orElseThrow();

        assertThat(latest.id()).isEqualTo(higherId);
    }

    private UUID registerReservation() {
        UUID reservationId = UUID.randomUUID();
        reservationAdapter.save(ReservationInformation.create(new ReservationId(reservationId),
                new BoatId(UUID.randomUUID()), new BaseRate(new Money(new BigDecimal("350000.00"))), null,
                LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 12), 2, new OwnerId(UUID.randomUUID()), 4));
        return reservationId;
    }

    private static ChargeIntent intent(UUID reservationId, UUID intentId, Instant createdAt) {
        return ChargeIntent.create(intentId, new ReservationId(reservationId),
                new IdempotencyKey("key-" + intentId), TOTAL,
                new PaymentMethodReference("token-123", "CREDIT_CARD", "4242"), createdAt);
    }
}
