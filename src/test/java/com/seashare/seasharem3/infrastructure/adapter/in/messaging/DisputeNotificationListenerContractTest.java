package com.seashare.seasharem3.infrastructure.adapter.in.messaging;

import com.seashare.seasharem3.application.dto.DisputeNotificationCommand;
import com.seashare.seasharem3.application.port.in.HandleDisputeNotificationUseCase;
import com.seashare.seasharem3.domain.exception.DisputeNotificationValidationException;
import com.seashare.seasharem3.domain.valueobject.DisputeId;
import com.seashare.seasharem3.domain.valueobject.DisputeStatus;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto.DisputeNotificationMessage;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto.DisputeNotificationMessageMapper;
import com.seashare.seasharem3.infrastructure.config.amqp.AmqpListenerFailurePolicy;
import com.seashare.seasharem3.infrastructure.config.amqp.AmqpNames;
import com.seashare.seasharem3.support.InMemoryFailureRecorder;
import com.seashare.seasharem3.support.MessagingJsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Contrato del evento {@code reservation.dispute.updated} y clasificacion del listener: cuerpo y
 * headers validos, campos ausentes, estados desconocidos, JSON/UUID/instante invalidos y errores
 * permanentes ({@code ack} + registro) frente a transitorios (propagacion para reintentos/DLQ).
 */
class DisputeNotificationListenerContractTest {

    private static final UUID RESERVATION_UUID = UUID.fromString("b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10");
    private static final UUID DISPUTE_UUID = UUID.fromString("d8e1f2a3-b4c5-6d7e-8f9a-0b1c2d3e4f5a");
    private static final Instant STATUS_CHANGED_AT = Instant.parse("2026-12-23T10:00:00Z");
    private static final String EVENT_KEY = "v1.0-4a5b6c";

    private static final String CONTRACT_JSON = """
            {
              "reservation_id": "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10",
              "dispute_id": "d8e1f2a3-b4c5-6d7e-8f9a-0b1c2d3e4f5a",
              "status": "REJECTED",
              "status_changed_at": "2026-12-23T10:00:00Z",
              "event_key": "v1.0-4a5b6c"
            }
            """;

    private static final String JSON_WITHOUT_EVENT_KEY = """
            {
              "reservation_id": "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10",
              "dispute_id": "d8e1f2a3-b4c5-6d7e-8f9a-0b1c2d3e4f5a",
              "status": "REJECTED",
              "status_changed_at": "2026-12-23T10:00:00Z"
            }
            """;

    private final JsonMapper jsonMapper = MessagingJsonMapper.create();
    private HandleDisputeNotificationUseCase useCase;
    private InMemoryFailureRecorder failureRecorder;
    private DisputeNotificationListener listener;

    @BeforeEach
    void setUp() {
        useCase = mock(HandleDisputeNotificationUseCase.class);
        failureRecorder = new InMemoryFailureRecorder();
        listener = new DisputeNotificationListener(useCase, new DisputeNotificationMessageMapper(),
                new AmqpListenerFailurePolicy(failureRecorder), jsonMapper);
    }

    @Test
    void deserializesTheContractBody() {
        DisputeNotificationMessage message = jsonMapper.readValue(CONTRACT_JSON, DisputeNotificationMessage.class);

        assertThat(message.reservationId()).isEqualTo(RESERVATION_UUID);
        assertThat(message.disputeId()).isEqualTo(DISPUTE_UUID);
        assertThat(message.status()).isEqualTo("REJECTED");
        assertThat(message.statusChangedAt()).isEqualTo(STATUS_CHANGED_AT);
        assertThat(message.eventKey()).isEqualTo(EVENT_KEY);
    }

    @Test
    void mapsTheContractBodyToTheApplicationCommand() {
        DisputeNotificationMessage message = jsonMapper.readValue(CONTRACT_JSON, DisputeNotificationMessage.class);

        DisputeNotificationCommand command = new DisputeNotificationMessageMapper().toCommand(message);

        assertThat(command).isEqualTo(new DisputeNotificationCommand(new ReservationId(RESERVATION_UUID),
                new DisputeId(DISPUTE_UUID), DisputeStatus.REJECTED, STATUS_CHANGED_AT, EVENT_KEY));
    }

    @Test
    void acceptsJsonContentTypeWithCharsetAndDispatchesTheCommand() {
        listener.onMessage(message(CONTRACT_JSON, "application/json;charset=UTF-8", "msg-1"));

        verify(useCase).handle(new DisputeNotificationCommand(new ReservationId(RESERVATION_UUID),
                new DisputeId(DISPUTE_UUID), DisputeStatus.REJECTED, STATUS_CHANGED_AT, EVENT_KEY));
        assertThat(failureRecorder.failures()).isEmpty();
    }

    @Test
    void rejectsMissingContentType() {
        listener.onMessage(message(CONTRACT_JSON, null, "msg-1"));

        verifyNoInteractions(useCase);
        assertFailureRecorded(null);
    }

    @Test
    void rejectsMissingMessageId() {
        listener.onMessage(message(CONTRACT_JSON, "application/json", null));

        verifyNoInteractions(useCase);
        assertFailureRecorded(null);
    }

    @Test
    void acknowledgesMalformedJson() {
        assertThatCode(() -> listener.onMessage(message("{not-json", "application/json", "msg-1")))
                .doesNotThrowAnyException();

        verifyNoInteractions(useCase);
        assertFailureRecorded(null);
    }

    @Test
    void acknowledgesMissingEventKey() {
        assertThatCode(() -> listener.onMessage(message(JSON_WITHOUT_EVENT_KEY, "application/json", "msg-1")))
                .doesNotThrowAnyException();

        verifyNoInteractions(useCase);
        assertFailureRecorded(RESERVATION_UUID);
    }

    @Test
    void acknowledgesUnknownStatus() {
        String body = CONTRACT_JSON.replace("\"REJECTED\"", "\"PARTIALLY_RELEASED\"");

        assertThatCode(() -> listener.onMessage(message(body, "application/json", "msg-1")))
                .doesNotThrowAnyException();

        verifyNoInteractions(useCase);
        assertFailureRecorded(RESERVATION_UUID);
    }

    @Test
    void acknowledgesNumericReservationId() {
        String body = CONTRACT_JSON.replace("\"reservation_id\": \"" + RESERVATION_UUID + "\"",
                "\"reservation_id\": 123");

        assertThatCode(() -> listener.onMessage(message(body, "application/json", "msg-1")))
                .doesNotThrowAnyException();

        verifyNoInteractions(useCase);
        assertFailureRecorded(null);
    }

    @Test
    void acknowledgesInvalidUuid() {
        String body = CONTRACT_JSON.replace(RESERVATION_UUID.toString(), "not-a-uuid");

        assertThatCode(() -> listener.onMessage(message(body, "application/json", "msg-1")))
                .doesNotThrowAnyException();

        verifyNoInteractions(useCase);
        assertFailureRecorded(null);
    }

    @Test
    void acknowledgesInvalidStatusChangedAt() {
        String body = CONTRACT_JSON.replace("\"2026-12-23T10:00:00Z\"", "\"23-12-2026\"");

        assertThatCode(() -> listener.onMessage(message(body, "application/json", "msg-1")))
                .doesNotThrowAnyException();

        verifyNoInteractions(useCase);
        assertFailureRecorded(RESERVATION_UUID);
    }

    @Test
    void acknowledgesUnknownProperties() {
        String body = CONTRACT_JSON.replace("}", ",\"extra\":\"not-in-contract\"}");

        assertThatCode(() -> listener.onMessage(message(body, "application/json", "msg-1")))
                .doesNotThrowAnyException();

        verifyNoInteractions(useCase);
        assertFailureRecorded(RESERVATION_UUID);
    }

    @Test
    void acknowledgesValidationFailureFromTheService() {
        doThrow(new DisputeNotificationValidationException("bad event")).when(useCase).handle(any());

        assertThatCode(() -> listener.onMessage(message(CONTRACT_JSON, "application/json", "msg-1")))
                .doesNotThrowAnyException();

        verify(useCase).handle(any());
        assertFailureRecorded(RESERVATION_UUID);
    }

    @Test
    void propagatesUnexpectedRuntimeFailures() {
        doThrow(new IllegalStateException("database down")).when(useCase).handle(any());

        assertThatThrownBy(() -> listener.onMessage(message(CONTRACT_JSON, "application/json", "msg-1")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(failureRecorder.failures()).isEmpty();
    }

    @Test
    void propagatesPersistenceFailures() {
        doThrow(new DataAccessResourceFailureException("database down")).when(useCase).handle(any());

        assertThatThrownBy(() -> listener.onMessage(message(CONTRACT_JSON, "application/json", "msg-1")))
                .isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(failureRecorder.failures()).isEmpty();
    }

    @Test
    void acknowledgesADisputeForAReservationThatUc03HasNotRegistered() {
        doThrow(new DataIntegrityViolationException("foreign key",
                new SQLException("violates fk_dispute_event_log_reservation", "23503")))
                .when(useCase).handle(any());

        assertThatCode(() -> listener.onMessage(message(CONTRACT_JSON, "application/json", "msg-1")))
                .doesNotThrowAnyException();

        assertFailureRecorded(RESERVATION_UUID);
    }

    @Test
    void propagatesOtherDataIntegrityViolations() {
        doThrow(new DataIntegrityViolationException("not null",
                new SQLException("null value in column event_key", "23502")))
                .when(useCase).handle(any());

        assertThatThrownBy(() -> listener.onMessage(message(CONTRACT_JSON, "application/json", "msg-1")))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(failureRecorder.failures()).isEmpty();
    }

    @Test
    void recordsTheMessageIdInThePayloadRef() {
        listener.onMessage(message("{not-json", "application/json", "msg-123"));

        assertThat(failureRecorder.failures()).singleElement().satisfies(failure -> assertThat(failure.payloadRef())
                .contains("queue=" + AmqpNames.QUEUE_GUARANTEE_DISPUTE)
                .contains("messageId=msg-123"));
    }

    private void assertFailureRecorded(UUID expectedReservationId) {
        assertThat(failureRecorder.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.useCase()).isEqualTo("UC08");
            assertThat(failure.reservationId()).isEqualTo(expectedReservationId);
            assertThat(failure.reason()).isNotBlank();
            assertThat(failure.payloadRef()).contains("queue=" + AmqpNames.QUEUE_GUARANTEE_DISPUTE);
        });
    }

    private static Message message(String body, String contentType, String messageId) {
        MessageProperties properties = new MessageProperties();
        if (contentType != null) {
            properties.setContentType(contentType);
        }
        if (messageId != null) {
            properties.setMessageId(messageId);
        }
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}
