package com.seashare.seasharem3.infrastructure.adapter.in.messaging;

import com.seashare.seasharem3.application.port.in.SendChargeToGatewayUseCase;
import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.exception.GatewayCommunicationException;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto.ChargeCommandMessage;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto.ChargeCommandMessageMapper;
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
 * Adaptador de entrada de UC05: consume el comando de cobro de la cola interna
 * {@code finance.charge-command.v1} y delega en el puerto de entrada.
 *
 * <p>El cuerpo se parsea manualmente para clasificar un cuerpo mal formado como error permanente
 * (ack + registro) en vez de dejarlo llegar a la DLQ. Un fallo logico del cobro se registra y se
 * confirma; una falla de comunicacion con la pasarela se propaga para que la retry policy la
 * reintente con la misma clave idempotente. Fuente: UC05 plan T014 y T015; contracts/README §4.4.</p>
 */
@Component
public class ChargeCommandListener {

    private static final Logger log = LoggerFactory.getLogger(ChargeCommandListener.class);

    private static final String USE_CASE = "UC05";
    private static final String JSON_CONTENT_TYPE = "application/json";

    private final SendChargeToGatewayUseCase sendChargeToGateway;
    private final ChargeCommandMessageMapper mapper;
    private final AmqpListenerFailurePolicy failurePolicy;
    private final JsonMapper jsonMapper;

    public ChargeCommandListener(SendChargeToGatewayUseCase sendChargeToGateway, ChargeCommandMessageMapper mapper,
            AmqpListenerFailurePolicy failurePolicy, JsonMapper jsonMapper) {
        this.sendChargeToGateway = Objects.requireNonNull(sendChargeToGateway, "sendChargeToGateway");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.failurePolicy = Objects.requireNonNull(failurePolicy, "failurePolicy");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
    }

    @RabbitListener(queues = AmqpNames.QUEUE_CHARGE_COMMAND)
    public void onMessage(Message message) {
        MessageProperties properties = message.getMessageProperties();
        String messageId = properties.getMessageId();
        String payloadRef = payloadRef(messageId);

        log.info("Received charge command (messageId={})", messageId);

        if (!isJson(properties.getContentType()) || messageId == null || messageId.isBlank()) {
            recordAndAcknowledge(null, new IllegalArgumentException(
                    "charge.command requires Content-Type application/json and Message-Id"), payloadRef);
            return;
        }

        JsonNode root;
        try {
            root = jsonMapper.readTree(message.getBody());
        } catch (RuntimeException exception) {
            recordAndAcknowledge(null, new IllegalArgumentException(
                    "charge.command body is not valid JSON for the contract", exception), payloadRef);
            return;
        }
        if (root == null || !root.isObject()) {
            recordAndAcknowledge(null, new IllegalArgumentException(
                    "charge.command body must be a JSON object"), payloadRef);
            return;
        }

        ChargeCommandMessage payload;
        try {
            payload = jsonMapper.treeToValue(root, ChargeCommandMessage.class);
        } catch (RuntimeException exception) {
            recordAndAcknowledge(null, new IllegalArgumentException(
                    "charge.command body does not match the contract", exception), payloadRef);
            return;
        }
        if (payload == null || payload.chargeIntentId() == null) {
            recordAndAcknowledge(null, new IllegalArgumentException(
                    "charge.command body requires charge_intent_id"), payloadRef);
            return;
        }

        try {
            sendChargeToGateway.send(mapper.toCommand(payload));
        } catch (GatewayCommunicationException exception) {
            throw exception;
        } catch (DomainException exception) {
            recordAndAcknowledge(null, exception, payloadRef);
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
        return "queue=" + AmqpNames.QUEUE_CHARGE_COMMAND + (messageId != null ? ";messageId=" + messageId : "");
    }
}
