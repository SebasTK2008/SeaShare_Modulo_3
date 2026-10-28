package com.seashare.seasharem3.infrastructure.adapter.in.messaging;

import com.seashare.seasharem3.application.dto.RegisterReservationInformationCommand;
import com.seashare.seasharem3.application.port.in.RegisterReservationInformationUseCase;
import com.seashare.seasharem3.domain.exception.BaseRateException;
import com.seashare.seasharem3.domain.exception.BaseRateException.BaseRateFailureReason;
import com.seashare.seasharem3.domain.exception.ReservationInfoIncompleteException;
import com.seashare.seasharem3.domain.exception.ReservationInformationValidationException;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto.ReservationInfoMessage;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto.ReservationInfoMessageMapper;
import com.seashare.seasharem3.infrastructure.config.amqp.AmqpListenerFailurePolicy;
import com.seashare.seasharem3.infrastructure.config.amqp.AmqpNames;
import com.seashare.seasharem3.support.InMemoryFailureRecorder;
import com.seashare.seasharem3.support.MessagingJsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
 * Contrato del evento {@code reservation.info.provided} y clasificación del listener:
 * cuerpo/headers válidos, campos ausentes, JSON mal formado, UUID/fecha inválidos y errores
 * permanentes (ack + registro) frente a transitorios (propagación para reintentos/DLQ).
 */
class ReservationInfoListenerContractTest {

    private static final UUID RESERVATION_UUID = UUID.fromString("b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10");
    private static final UUID BOAT_UUID = UUID.fromString("3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01");
    private static final UUID OWNER_UUID = UUID.fromString("a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d");
    private static final LocalDate START = LocalDate.of(2026, 12, 20);
    private static final LocalDate END = LocalDate.of(2026, 12, 22);

    private static final String CONTRACT_JSON = """
            {
              "reservation_id": "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10",
              "boat_id": "3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01",
              "start_date": "2026-12-20",
              "end_date": "2026-12-22",
              "passengers": 4,
              "owner_id": "a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d",
              "max_capacity": 6
            }
            """;

    private static final String JSON_WITHOUT_OWNER = """
            {
              "reservation_id": "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10",
              "boat_id": "3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01",
              "start_date": "2026-12-20",
              "end_date": "2026-12-22",
              "passengers": 4,
              "max_capacity": 6
            }
            """;

    private static final String JSON_WITHOUT_CAPACITY = """
            {
              "reservation_id": "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10",
              "boat_id": "3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01",
              "start_date": "2026-12-20",
              "end_date": "2026-12-22",
              "passengers": 4,
              "owner_id": "a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d"
            }
            """;

    private static final String JSON_WITHOUT_PASSENGERS = """
            {
              "reservation_id": "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10",
              "boat_id": "3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01",
              "start_date": "2026-12-20",
              "end_date": "2026-12-22",
              "owner_id": "a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d",
              "max_capacity": 6
            }
            """;

    private final JsonMapper jsonMapper = MessagingJsonMapper.create();
    private RegisterReservationInformationUseCase useCase;
    private InMemoryFailureRecorder failureRecorder;
    private ReservationInfoListener listener;

    @BeforeEach
    void setUp() {
        useCase = mock(RegisterReservationInformationUseCase.class);
        failureRecorder = new InMemoryFailureRecorder();
        listener = new ReservationInfoListener(useCase, new ReservationInfoMessageMapper(),
                new AmqpListenerFailurePolicy(failureRecorder), jsonMapper);
    }

    @Test
    void deserializesTheContractBody() {
        ReservationInfoMessage message = jsonMapper.readValue(CONTRACT_JSON, ReservationInfoMessage.class);

        assertThat(message.reservationId()).isEqualTo(RESERVATION_UUID);
        assertThat(message.boatId()).isEqualTo(BOAT_UUID);
        assertThat(message.startDate()).isEqualTo(START);
        assertThat(message.endDate()).isEqualTo(END);
        assertThat(message.passengers()).isEqualTo(4);
        assertThat(message.ownerId()).isEqualTo(OWNER_UUID);
        assertThat(message.maxCapacity()).isEqualTo(6);
    }

    @Test
    void mapsTheContractBodyToTheApplicationCommand() {
        ReservationInfoMessage message = jsonMapper.readValue(CONTRACT_JSON, ReservationInfoMessage.class);

        RegisterReservationInformationCommand command = new ReservationInfoMessageMapper().toCommand(message);

        assertThat(command).isEqualTo(new RegisterReservationInformationCommand(
                new ReservationId(RESERVATION_UUID), new BoatId(BOAT_UUID), START, END, 4,
                new OwnerId(OWNER_UUID), 6));
    }

    @Test
    void acceptsJsonContentTypeWithCharsetAndDispatchesTheCommand() {
        listener.onMessage(message(CONTRACT_JSON, "application/json;charset=UTF-8", "msg-1"));

        verify(useCase).register(new RegisterReservationInformationCommand(
                new ReservationId(RESERVATION_UUID), new BoatId(BOAT_UUID), START, END, 4,
                new OwnerId(OWNER_UUID), 6));
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
    void acknowledgesMissingOwnerId() {
        assertThatCode(() -> listener.onMessage(message(JSON_WITHOUT_OWNER, "application/json", "msg-1")))
                .doesNotThrowAnyException();

        verifyNoInteractions(useCase);
        assertFailureRecorded(RESERVATION_UUID);
    }

    @Test
    void acknowledgesMissingMaxCapacity() {
        assertThatCode(() -> listener.onMessage(message(JSON_WITHOUT_CAPACITY, "application/json", "msg-1")))
                .doesNotThrowAnyException();

        verifyNoInteractions(useCase);
        assertFailureRecorded(RESERVATION_UUID);
    }

    @Test
    void acknowledgesMissingPassengers() {
        assertThatCode(() -> listener.onMessage(message(JSON_WITHOUT_PASSENGERS, "application/json", "msg-1")))
                .doesNotThrowAnyException();

        verifyNoInteractions(useCase);
        assertFailureRecorded(RESERVATION_UUID);
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
    void acknowledgesInvalidDate() {
        String body = CONTRACT_JSON.replace("\"2026-12-20\"", "\"20-12-2026\"");

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
    void acknowledgesNullBody() {
        assertThatCode(() -> listener.onMessage(message("null", "application/json", "msg-1")))
                .doesNotThrowAnyException();

        verifyNoInteractions(useCase);
        assertFailureRecorded(null);
    }

    @Test
    void acknowledgesPermanentBaseRateFailure() {
        doThrow(new BaseRateException(BaseRateFailureReason.BASE_RATE_NOT_AVAILABLE, "no rate"))
                .when(useCase).register(any());

        assertThatCode(() -> listener.onMessage(message(CONTRACT_JSON, "application/json", "msg-1")))
                .doesNotThrowAnyException();

        verify(useCase).register(any());
        assertFailureRecorded(RESERVATION_UUID);
    }

    @Test
    void propagatesTransientBaseRateFailure() {
        doThrow(new BaseRateException(BaseRateFailureReason.FLEET_UNAVAILABLE, "fleet down"))
                .when(useCase).register(any());

        assertThatThrownBy(() -> listener.onMessage(message(CONTRACT_JSON, "application/json", "msg-1")))
                .isInstanceOfSatisfying(BaseRateException.class,
                        exception -> assertThat(exception.isTransient()).isTrue());

        assertThat(failureRecorder.failures()).isEmpty();
    }

    @Test
    void acknowledgesValidationFailureFromTheService() {
        doThrow(new ReservationInformationValidationException("bad dates"))
                .when(useCase).register(any());

        assertThatCode(() -> listener.onMessage(message(CONTRACT_JSON, "application/json", "msg-1")))
                .doesNotThrowAnyException();

        assertFailureRecorded(RESERVATION_UUID);
    }

    @Test
    void acknowledgesIncompleteInformationFromTheService() {
        doThrow(new ReservationInfoIncompleteException("owner missing"))
                .when(useCase).register(any());

        assertThatCode(() -> listener.onMessage(message(CONTRACT_JSON, "application/json", "msg-1")))
                .doesNotThrowAnyException();

        assertFailureRecorded(RESERVATION_UUID);
    }

    @Test
    void propagatesUnexpectedRuntimeFailures() {
        doThrow(new IllegalStateException("database down")).when(useCase).register(any());

        assertThatThrownBy(() -> listener.onMessage(message(CONTRACT_JSON, "application/json", "msg-1")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(failureRecorder.failures()).isEmpty();
    }

    @Test
    void recordsTheMessageIdInThePayloadRef() {
        listener.onMessage(message("{not-json", "application/json", "msg-123"));

        assertThat(failureRecorder.failures()).singleElement()
                .satisfies(failure -> assertThat(failure.payloadRef())
                        .contains("queue=" + AmqpNames.QUEUE_RESERVATION_INFO)
                        .contains("messageId=msg-123"));
    }

    private void assertFailureRecorded(UUID expectedReservationId) {
        assertThat(failureRecorder.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.useCase()).isEqualTo("UC03");
            assertThat(failure.reservationId()).isEqualTo(expectedReservationId);
            assertThat(failure.reason()).isNotBlank();
            assertThat(failure.payloadRef()).contains("queue=" + AmqpNames.QUEUE_RESERVATION_INFO);
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
