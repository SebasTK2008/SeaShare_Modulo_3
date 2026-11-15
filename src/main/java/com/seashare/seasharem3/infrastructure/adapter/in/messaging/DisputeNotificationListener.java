package com.seashare.seasharem3.infrastructure.adapter.in.messaging;

import com.seashare.seasharem3.application.port.in.HandleDisputeNotificationUseCase;
import com.seashare.seasharem3.domain.exception.DisputeNotificationValidationException;
import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto.DisputeNotificationMessage;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto.DisputeNotificationMessageMapper;
import com.seashare.seasharem3.infrastructure.config.amqp.AmqpListenerFailurePolicy;
import com.seashare.seasharem3.infrastructure.config.amqp.AmqpNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Adaptador de entrada de UC08: consume {@code reservation.dispute.updated} de la cola
 * {@code finance.guarantee-dispute.v1} y delega en el puerto de entrada.
 *
 * <p>El mensaje se parsea manualmente para clasificar un cuerpo mal formado como error permanente
 * ({@code ack} + registro) en vez de dejarlo llegar a la DLQ. Los fallos permanentes (contrato,
 * estado no canonico, conflicto de idempotencia) se registran con la politica compartida y se
 * confirman; los transitorios se propagan para que la retry policy los reintente y el recoverer los
 * registre y los rechace a la DLQ. Fuente: UC08 plan T010, RF-009, RNF-003; contracts/README §4.4.</p>
 */
@Component
public class DisputeNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(DisputeNotificationListener.class);

    private static final String USE_CASE = "UC08";
    private static final String JSON_CONTENT_TYPE = "application/json";

    private final HandleDisputeNotificationUseCase useCase;
    private final DisputeNotificationMessageMapper mapper;
    private final AmqpListenerFailurePolicy failurePolicy;
    private final JsonMapper jsonMapper;

    public DisputeNotificationListener(HandleDisputeNotificationUseCase useCase,
            DisputeNotificationMessageMapper mapper, AmqpListenerFailurePolicy failurePolicy,
            JsonMapper jsonMapper) {
        this.useCase = Objects.requireNonNull(useCase, "useCase");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.failurePolicy = Objects.requireNonNull(failurePolicy, "failurePolicy");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
    }

    @RabbitListener(queues = AmqpNames.QUEUE_GUARANTEE_DISPUTE)
    public void onMessage(Message message) {
        MessageProperties properties = message.getMessageProperties();
        String messageId = properties.getMessageId();
        String payloadRef = payloadRef(messageId);

        log.info("Received guarantee dispute event (messageId={})", messageId);

        if (!isJson(properties.getContentType()) || messageId == null || messageId.isBlank()) {
            recordAndAcknowledge(null, new DisputeNotificationValidationException(
                    "reservation.dispute.updated requires Content-Type application/json and Message-Id"),
                    payloadRef);
            return;
        }

        JsonNode root;
        try {
            root = jsonMapper.readTree(message.getBody());
        } catch (RuntimeException exception) {
            recordAndAcknowledge(null, new DisputeNotificationValidationException(
                    "reservation.dispute.updated body is not valid JSON for the contract", exception), payloadRef);
            return;
        }
        if (root == null || !root.isObject()) {
            recordAndAcknowledge(null, new DisputeNotificationValidationException(
                    "reservation.dispute.updated body must be a JSON object"), payloadRef);
            return;
        }

        UUID reservationId = reservationIdOrNull(root);
        DisputeNotificationMessage payload;
        try {
            validateScalarTypes(root);
            payload = jsonMapper.treeToValue(root, DisputeNotificationMessage.class);
        } catch (DisputeNotificationValidationException exception) {
            recordAndAcknowledge(reservationId, exception, payloadRef);
            return;
        } catch (RuntimeException exception) {
            recordAndAcknowledge(reservationId, new DisputeNotificationValidationException(
                    "reservation.dispute.updated body does not match the contract", exception), payloadRef);
            return;
        }
        if (payload == null) {
            recordAndAcknowledge(reservationId, new DisputeNotificationValidationException(
                    "reservation.dispute.updated body must be a JSON object"), payloadRef);
            return;
        }

        log.info("Guarantee dispute event parsed (messageId={}, reservationId={}, status={})", messageId,
                reservationId, payload.status());

        try {
            useCase.handle(mapper.toCommand(payload));
        } catch (DomainException exception) {
            recordAndAcknowledge(payload.reservationId(), exception, payloadRef);
        } catch (DataIntegrityViolationException exception) {
            if (isMissingReservation(exception)) {
                recordAndAcknowledge(payload.reservationId(), exception, payloadRef);
                return;
            }
            throw exception;
        }
    }

    /**
     * La FK de {@code dispute_event_log} exige que UC03 haya registrado la reserva. Una disputa de
     * una reserva desconocida es un error permanente (ack + registro), no una falla transitoria que
     * deba ir a la DLQ (contracts/README §4.4).
     */
    private static boolean isMissingReservation(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (!(root instanceof SQLException sqlException)) {
            return false;
        }
        return "23503".equals(sqlException.getSQLState())
                && sqlException.getMessage() != null
                && sqlException.getMessage().contains("fk_dispute_event_log_reservation");
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
        return "queue=" + AmqpNames.QUEUE_GUARANTEE_DISPUTE
                + (messageId != null ? ";messageId=" + messageId : "");
    }

    private static void validateScalarTypes(JsonNode root) {
        requireText(root, "reservation_id");
        requireText(root, "dispute_id");
        requireText(root, "status");
        requireText(root, "status_changed_at");
        requireText(root, "event_key");
    }

    private static void requireText(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value != null && !value.isNull() && !value.isString()) {
            throw new DisputeNotificationValidationException(field + " must be a string");
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
