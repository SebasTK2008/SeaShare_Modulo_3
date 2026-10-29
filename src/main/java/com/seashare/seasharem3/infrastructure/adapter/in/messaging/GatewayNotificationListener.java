package com.seashare.seasharem3.infrastructure.adapter.in.messaging;

import com.seashare.seasharem3.application.port.in.GatewayWebhookHandler;
import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.exception.GatewayCommunicationException;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto.GatewayNotificationMessage;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto.GatewayNotificationMessageMapper;
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

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Worker de las notificaciones del webhook de la pasarela: consume la cola interna
 * {@code finance.gateway-notification.v1}, que el webhook publica de forma durable antes de acusar
 * recibo, y despacha la notificacion al manejador del {@code type} correspondiente.
 *
 * <p>El paso 2 del contrato (consulta del estado real del pago a la pasarela) ocurre aqui, de forma
 * asincrona, para que la pasarela no espere al sistema. Un fallo transitorio se propaga para que la
 * retry policy lo reintente; un fallo logico se registra y se confirma.
 * Fuente: UC05 plan T020; contrato {@code pasarela-webhook-resultados.md} §3.2 y §3.3.</p>
 */
@Component
public class GatewayNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(GatewayNotificationListener.class);

    private static final String USE_CASE = "UC05";
    private static final String JSON_CONTENT_TYPE = "application/json";

    private final List<GatewayWebhookHandler> handlers;
    private final GatewayNotificationMessageMapper mapper;
    private final AmqpListenerFailurePolicy failurePolicy;
    private final JsonMapper jsonMapper;

    public GatewayNotificationListener(List<GatewayWebhookHandler> handlers,
            GatewayNotificationMessageMapper mapper, AmqpListenerFailurePolicy failurePolicy,
            JsonMapper jsonMapper) {
        this.handlers = List.copyOf(handlers);
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.failurePolicy = Objects.requireNonNull(failurePolicy, "failurePolicy");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
    }

    @RabbitListener(queues = AmqpNames.QUEUE_GATEWAY_NOTIFICATION)
    public void onMessage(Message message) {
        MessageProperties properties = message.getMessageProperties();
        String messageId = properties.getMessageId();
        String payloadRef = payloadRef(messageId);

        log.info("Received gateway notification (messageId={})", messageId);

        if (!isJson(properties.getContentType()) || messageId == null || messageId.isBlank()) {
            recordAndAcknowledge(new IllegalArgumentException(
                    "gateway.notification requires Content-Type application/json and Message-Id"), payloadRef);
            return;
        }

        JsonNode root;
        try {
            root = jsonMapper.readTree(message.getBody());
        } catch (RuntimeException exception) {
            recordAndAcknowledge(new IllegalArgumentException(
                    "gateway.notification body is not valid JSON for the contract", exception), payloadRef);
            return;
        }
        if (root == null || !root.isObject()) {
            recordAndAcknowledge(new IllegalArgumentException(
                    "gateway.notification body must be a JSON object"), payloadRef);
            return;
        }

        GatewayNotificationMessage payload;
        try {
            payload = jsonMapper.treeToValue(root, GatewayNotificationMessage.class);
        } catch (RuntimeException exception) {
            recordAndAcknowledge(new IllegalArgumentException(
                    "gateway.notification body does not match the contract", exception), payloadRef);
            return;
        }
        if (payload == null || payload.type() == null) {
            recordAndAcknowledge(new IllegalArgumentException(
                    "gateway.notification body requires type"), payloadRef);
            return;
        }

        Optional<GatewayWebhookHandler> handler = handlers.stream()
                .filter(candidate -> candidate.supportedType().equals(payload.type()))
                .findFirst();
        if (handler.isEmpty()) {
            recordAndAcknowledge(new IllegalArgumentException(
                    "There is no handler for the gateway notification type " + payload.type()), payloadRef);
            return;
        }

        try {
            handler.get().handle(mapper.toNotification(payload));
        } catch (GatewayCommunicationException exception) {
            throw exception;
        } catch (DomainException exception) {
            recordAndAcknowledge(exception, payloadRef);
        }
    }

    private static boolean isJson(String contentType) {
        if (contentType == null) {
            return false;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT).trim();
        return normalized.equals(JSON_CONTENT_TYPE) || normalized.startsWith(JSON_CONTENT_TYPE + ";");
    }

    private void recordAndAcknowledge(Throwable failure, String payloadRef) {
        failurePolicy.recordAndAcknowledge(USE_CASE, null, failure, payloadRef);
    }

    private static String payloadRef(String messageId) {
        return "queue=" + AmqpNames.QUEUE_GATEWAY_NOTIFICATION
                + (messageId != null ? ";messageId=" + messageId : "");
    }
}
