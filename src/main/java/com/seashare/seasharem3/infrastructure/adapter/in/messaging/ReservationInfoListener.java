package com.seashare.seasharem3.infrastructure.adapter.in.messaging;

import com.seashare.seasharem3.application.port.in.RegisterReservationInformationUseCase;
import com.seashare.seasharem3.domain.exception.BaseRateException;
import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.exception.ReservationInformationValidationException;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto.ReservationInfoMessage;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto.ReservationInfoMessageMapper;
import com.seashare.seasharem3.infrastructure.config.amqp.AmqpListenerFailurePolicy;
import com.seashare.seasharem3.infrastructure.config.amqp.AmqpNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Adaptador de entrada de UC03: consume {@code reservation.info.provided} de la cola
 * {@code finance.reservation-info.v1} y delega en el puerto de entrada.
 *
 * <p>El mensaje se parsea manualmente para poder clasificar un cuerpo mal formado como error
 * permanente ({@code ack} + registro) en vez de dejarlo llegar a la DLQ. Los fallos permanentes
 * (validación, datos incompletos, tarifa no disponible) se registran con la política compartida y se
 * confirman; los transitorios se propagan para que la retry policy los reintente y el recoverer los
 * registre y los rechace a la DLQ. Fuente: UC03 plan T011, RF-006, RNF-003; contracts/README §4.4.</p>
 */
@Component
public class ReservationInfoListener {

    private static final Logger log = LoggerFactory.getLogger(ReservationInfoListener.class);

    private static final String USE_CASE = "UC03";
    private static final String JSON_CONTENT_TYPE = "application/json";

    private final RegisterReservationInformationUseCase useCase;
    private final ReservationInfoMessageMapper mapper;
    private final AmqpListenerFailurePolicy failurePolicy;
    private final JsonMapper jsonMapper;

    public ReservationInfoListener(RegisterReservationInformationUseCase useCase,
            ReservationInfoMessageMapper mapper,
            AmqpListenerFailurePolicy failurePolicy,
            JsonMapper jsonMapper) {
        this.useCase = Objects.requireNonNull(useCase, "useCase");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.failurePolicy = Objects.requireNonNull(failurePolicy, "failurePolicy");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
    }

    @RabbitListener(queues = AmqpNames.QUEUE_RESERVATION_INFO)
    public void onMessage(Message message) {
        MessageProperties properties = message.getMessageProperties();
        String messageId = properties.getMessageId();
        String payloadRef = payloadRef(messageId);

        log.info("Received reservation info event (messageId={})", messageId);

        if (!isJson(properties.getContentType()) || messageId == null || messageId.isBlank()) {
            recordAndAcknowledge(null, new ReservationInformationValidationException(
                    "reservation.info.provided requires Content-Type application/json and Message-Id"), payloadRef);
            return;
        }

        JsonNode root;
        try {
            root = jsonMapper.readTree(message.getBody());
        } catch (RuntimeException exception) {
            recordAndAcknowledge(null, new ReservationInformationValidationException(
                    "reservation.info.provided body is not valid JSON for the contract", exception), payloadRef);
            return;
        }
        if (root == null || !root.isObject()) {
            recordAndAcknowledge(null, new ReservationInformationValidationException(
                    "reservation.info.provided body must be a JSON object"), payloadRef);
            return;
        }

        UUID reservationId = reservationIdOrNull(root);
        ReservationInfoMessage payload;
        try {
            validateScalarTypes(root);
            payload = jsonMapper.treeToValue(root, ReservationInfoMessage.class);
        } catch (ReservationInformationValidationException exception) {
            recordAndAcknowledge(reservationId, exception, payloadRef);
            return;
        } catch (RuntimeException exception) {
            recordAndAcknowledge(reservationId, new ReservationInformationValidationException(
                    "reservation.info.provided body does not match the contract", exception), payloadRef);
            return;
        }
        if (payload == null) {
            recordAndAcknowledge(reservationId, new ReservationInformationValidationException(
                    "reservation.info.provided body must be a JSON object"), payloadRef);
            return;
        }

        log.info("Reservation info event parsed (messageId={}, reservationId={})", messageId, reservationId);

        try {
            useCase.register(mapper.toCommand(payload));
        } catch (BaseRateException exception) {
            if (exception.isTransient()) {
                throw exception;
            }
            recordAndAcknowledge(payload.reservationId(), exception, payloadRef);
        } catch (DomainException exception) {
            recordAndAcknowledge(payload.reservationId(), exception, payloadRef);
        }
    }

    private static boolean isJson(String contentType) {
        if (contentType == null) {
            return false;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT).trim();
        return normalized.equals(JSON_CONTENT_TYPE) || normalized.startsWith(JSON_CONTENT_TYPE + ";");
    }

    private void recordAndAcknowledge(UUID reservationId, Throwable failure, String payloadRef) {
        failurePolicy.recordAndAcknowledge(USE_CASE, reservationId, failure, payloadRef);
    }

    private static String payloadRef(String messageId) {
        return "queue=" + AmqpNames.QUEUE_RESERVATION_INFO + (messageId != null ? ";messageId=" + messageId : "");
    }

    private static void validateScalarTypes(JsonNode root) {
        requireText(root, "reservation_id");
        requireText(root, "boat_id");
        requireText(root, "start_date");
        requireText(root, "end_date");
        requireText(root, "owner_id");
        requireIntegral(root, "passengers");
        requireIntegral(root, "max_capacity");
    }

    private static void requireText(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value != null && !value.isNull() && !value.isString()) {
            throw new ReservationInformationValidationException(field + " must be a string");
        }
    }

    private static void requireIntegral(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value != null && !value.isNull() && !value.isIntegralNumber()) {
            throw new ReservationInformationValidationException(field + " must be an integer");
        }
    }

    /** Identificador best effort para registrar un mensaje mal formado sin impedir el {@code ack}. */
    private static UUID reservationIdOrNull(JsonNode root) {
        JsonNode value = root.get("reservation_id");
        if (value == null || !value.isString()) {
            return null;
        }
        try {
            return UUID.fromString(value.stringValue());
        } catch (IllegalArgumentException notUuid) {
            return null;
        }
    }
}
