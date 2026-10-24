package com.seashare.seasharem3.infrastructure.config.amqp;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

/**
 * Recoverer único de los listeners AMQP: cuando la retry policy agota los intentos de una falla
 * transitoria, registra el fallo en {@code operational_failure} y rechaza el mensaje para que vaya a la
 * DLQ de su cola (contracts/README §4.4; general-plan §7.2). Boot inyecta este bean en las factories de
 * listeners; sin él, el recoverer por defecto rechaza a la DLQ sin registrar el fallo.
 */
@Component
public class OperationalFailureMessageRecoverer implements MessageRecoverer {

    /** Cola de trabajo → caso de uso dueño; fuente: contracts/README §3.5 y general-plan §7.2. */
    private static final Map<String, String> USE_CASE_BY_QUEUE = Map.of(
            AmqpNames.QUEUE_RESERVATION_INFO, "UC03",
            AmqpNames.QUEUE_RESERVATION_STATUS, "UC07",
            AmqpNames.QUEUE_GUARANTEE_DISPUTE, "UC08",
            AmqpNames.QUEUE_CHARGE_COMMAND, "UC05",
            AmqpNames.QUEUE_REFUND_COMMAND, "UC09",
            AmqpNames.QUEUE_SETTLEMENT_COMMAND, "UC10");

    private static final String RESERVATION_ID_HEADER = "reservation_id";

    private final AmqpListenerFailurePolicy failurePolicy;
    private final JsonMapper jsonMapper;

    public OperationalFailureMessageRecoverer(AmqpListenerFailurePolicy failurePolicy, JsonMapper jsonMapper) {
        this.failurePolicy = failurePolicy;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void recover(Message message, Throwable cause) {
        MessageProperties properties = message.getMessageProperties();
        String queue = resolveQueue(properties);
        String useCase = USE_CASE_BY_QUEUE.getOrDefault(queue, "AMQP");
        failurePolicy.recordAndReject(useCase, resolveReservationId(properties, message.getBody()), cause,
                "queue=" + queue);
    }

    private static String resolveQueue(MessageProperties properties) {
        if (properties.getConsumerQueue() != null) {
            return properties.getConsumerQueue();
        }
        return properties.getReceivedRoutingKey() != null ? properties.getReceivedRoutingKey() : "unknown";
    }

    /**
     * Busca {@code reservation_id} en el header y, si no está, en el cuerpo JSON. Nunca falla:
     * el identificador es best effort y un mensaje malformado no puede impedir el rechazo a la DLQ.
     */
    private UUID resolveReservationId(MessageProperties properties, byte[] body) {
        Object header = properties.getHeaders().get(RESERVATION_ID_HEADER);
        if (header != null) {
            UUID fromHeader = parseUuid(header.toString());
            if (fromHeader != null) {
                return fromHeader;
            }
        }
        try {
            JsonNode node = jsonMapper.readTree(body);
            JsonNode value = node.get(RESERVATION_ID_HEADER);
            if (value != null && value.isString()) {
                return parseUuid(value.stringValue());
            }
        } catch (RuntimeException malformed) {
            // Sin identificador: el fallo se registra igual con reservation_id nulo.
        }
        return null;
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notUuid) {
            return null;
        }
    }
}
