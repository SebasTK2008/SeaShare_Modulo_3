package com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto;

import com.seashare.seasharem3.application.dto.ReservationStatusCommand;
import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.valueobject.ReservationStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pruebas del mapeo contrato AMQP -> comando de UC07 (plan T008). */
class ReservationStatusMessageMapperTest {

    private static final UUID RESERVATION_ID = UUID.fromString("b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10");
    private static final Instant STATUS_CHANGED_AT = Instant.parse("2026-12-05T14:30:00Z");

    private final ReservationStatusMessageMapper mapper = new ReservationStatusMessageMapper();

    @Test
    void mapsAPendingMessageWithItsPaymentData() {
        ReservationStatusCommand command = mapper.toCommand(new ReservationStatusMessage(RESERVATION_ID, "PENDING",
                STATUS_CHANGED_AT, "tok_12345", "CREDIT_CARD", Map.of("payer_email", "a@b.com")));

        assertThat(command.reservationId()).isEqualTo(RESERVATION_ID);
        assertThat(command.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(command.statusChangedAt()).isEqualTo(STATUS_CHANGED_AT);
        assertThat(command.paymentTokenRef()).isEqualTo("tok_12345");
        assertThat(command.paymentMethodType()).isEqualTo("CREDIT_CARD");
        assertThat(command.paymentMetadata()).containsEntry("payer_email", "a@b.com");
    }

    @Test
    void acceptsACompletedMessageWithoutPaymentData() {
        ReservationStatusCommand command = mapper.toCommand(
                new ReservationStatusMessage(RESERVATION_ID, "COMPLETED", STATUS_CHANGED_AT, null, null, null));

        assertThat(command.status()).isEqualTo(ReservationStatus.COMPLETED);
        assertThat(command.paymentMetadata()).isNull();
    }

    @Test
    void rejectsAnUnknownStatus() {
        assertThatThrownBy(() -> mapper.toCommand(
                new ReservationStatusMessage(RESERVATION_ID, "CANCELLED_MAYBE", STATUS_CHANGED_AT, null, null, null)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("CANCELLED_MAYBE");
    }

    @Test
    void rejectsAPendingMessageWithoutToken() {
        assertThatThrownBy(() -> mapper.toCommand(
                new ReservationStatusMessage(RESERVATION_ID, "PENDING", STATUS_CHANGED_AT, " ", null, null)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("payment_token_ref");
    }

    @Test
    void rejectsAMessageWithoutReservationOrTransitionInstant() {
        assertThatThrownBy(() -> mapper.toCommand(
                new ReservationStatusMessage(null, "COMPLETED", STATUS_CHANGED_AT, null, null, null)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("reservation_id");
        assertThatThrownBy(() -> mapper.toCommand(
                new ReservationStatusMessage(RESERVATION_ID, "COMPLETED", null, null, null, null)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("status_changed_at");
    }

    @Test
    void copiesThePaymentMetadataDefensively() {
        Map<String, String> metadata = new HashMap<>(Map.of("last_four", "4242"));
        ReservationStatusCommand command = mapper.toCommand(
                new ReservationStatusMessage(RESERVATION_ID, "PENDING", STATUS_CHANGED_AT, "tok", null, metadata));

        metadata.put("last_four", "0000");

        assertThat(command.paymentMetadata()).containsEntry("last_four", "4242");
    }
}
