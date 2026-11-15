package com.seashare.seasharem3.domain.model;

import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.valueobject.DisputeId;
import com.seashare.seasharem3.domain.valueobject.DisputeStatus;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisputeEventTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final ReservationId RESERVATION = new ReservationId(UUID.randomUUID());
    private static final DisputeId DISPUTE = new DisputeId(UUID.randomUUID());
    private static final String EVENT_KEY = "v1.0-4a5b6c";
    private static final Instant STATUS_CHANGED_AT = Instant.parse("2026-12-23T10:00:00Z");
    private static final Instant PROCESSED_AT = Instant.parse("2026-12-23T10:00:01Z");

    @Test
    void createsAnUnprocessedEvent() {
        DisputeEvent event = event(DisputeStatus.PENDING, EVENT_KEY);

        assertThat(event.id()).isEqualTo(EVENT_ID);
        assertThat(event.processedAt()).isNull();
        assertThat(event.status()).isEqualTo(DisputeStatus.PENDING);
    }

    @Test
    void markProcessedKeepsTheBusinessDataAndSetsTheTimestamp() {
        DisputeEvent processed = event(DisputeStatus.REJECTED, EVENT_KEY).markProcessed(PROCESSED_AT);

        assertThat(processed.processedAt()).isEqualTo(PROCESSED_AT);
        assertThat(processed.id()).isEqualTo(EVENT_ID);
        assertThat(processed.reservationId()).isEqualTo(RESERVATION);
        assertThat(processed.disputeId()).isEqualTo(DISPUTE);
        assertThat(processed.eventKey()).isEqualTo(EVENT_KEY);
        assertThat(processed.status()).isEqualTo(DisputeStatus.REJECTED);
        assertThat(processed.statusChangedAt()).isEqualTo(STATUS_CHANGED_AT);
    }

    @Test
    void rejectsABlankEventKey() {
        assertThatThrownBy(() -> event(DisputeStatus.PENDING, " "))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("eventKey");
    }

    @Test
    void rejectsAnEventKeyLongerThanTheDatabaseColumn() {
        String tooLong = "x".repeat(129);

        assertThatThrownBy(() -> event(DisputeStatus.PENDING, tooLong))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("128");
    }

    @Test
    void rejectsANullStatus() {
        assertThatThrownBy(() -> new DisputeEvent(EVENT_ID, RESERVATION, DISPUTE, EVENT_KEY, null,
                STATUS_CHANGED_AT, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("status");
    }

    @Test
    void rejectsANullStatusChangedAt() {
        assertThatThrownBy(() -> new DisputeEvent(EVENT_ID, RESERVATION, DISPUTE, EVENT_KEY, DisputeStatus.PENDING,
                null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("statusChangedAt");
    }

    @Test
    void samePayloadComparesStatusAndTransitionInstant() {
        DisputeEvent event = event(DisputeStatus.REJECTED, EVENT_KEY);

        assertThat(event.samePayload(event(DisputeStatus.REJECTED, EVENT_KEY))).isTrue();
        assertThat(event.samePayload(event(DisputeStatus.COMPLETED, EVENT_KEY))).isFalse();
        assertThat(event.samePayload(new DisputeEvent(UUID.randomUUID(), RESERVATION, DISPUTE, EVENT_KEY,
                DisputeStatus.REJECTED, STATUS_CHANGED_AT.plusSeconds(1), null))).isFalse();
        assertThat(event.samePayload(null)).isFalse();
    }

    private static DisputeEvent event(DisputeStatus status, String eventKey) {
        return new DisputeEvent(EVENT_ID, RESERVATION, DISPUTE, eventKey, status, STATUS_CHANGED_AT, null);
    }
}
