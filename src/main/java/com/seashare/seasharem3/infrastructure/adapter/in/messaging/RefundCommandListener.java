package com.seashare.seasharem3.infrastructure.adapter.in.messaging;

import com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto.RefundCommandMessage;
import com.seashare.seasharem3.application.port.in.SendRefundToGatewayUseCase;
import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.exception.GatewayCommunicationException;
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

/** Adaptador de entrada del worker de reembolsos: consume finance.refund-command.v1. */
@Component
public class RefundCommandListener {

    private static final Logger log = LoggerFactory.getLogger(RefundCommandListener.class);
    private static final String USE_CASE = "UC09";
    private static final String JSON_CONTENT_TYPE = "application/json";

    private final SendRefundToGatewayUseCase sendRefundToGateway;
    private final AmqpListenerFailurePolicy failurePolicy;
    private final JsonMapper jsonMapper;

    public RefundCommandListener(SendRefundToGatewayUseCase sendRefundToGateway,
            AmqpListenerFailurePolicy failurePolicy, JsonMapper jsonMapper) {
        this.sendRefundToGateway = Objects.requireNonNull(sendRefundToGateway, "sendRefundToGateway");
        this.failurePolicy = Objects.requireNonNull(failurePolicy, "failurePolicy");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
    }

    @RabbitListener(queues = AmqpNames.QUEUE_REFUND_COMMAND)
    public void onMessage(Message message) {
        MessageProperties properties = message.getMessageProperties();
        String messageId = properties.getMessageId();
        String payloadRef = payloadRef(messageId);
        log.info("Received refund command (messageId={})", messageId);

        if (!isJson(properties.getContentType()) || messageId == null || messageId.isBlank()) {
            recordAndAcknowledge(new IllegalArgumentException(
                    "refund.command requires Content-Type application/json and Message-Id"), payloadRef);
            return;
        }

        JsonNode root;
        try {
            root = jsonMapper.readTree(message.getBody());
        } catch (RuntimeException exception) {
            recordAndAcknowledge(new IllegalArgumentException(
                    "refund.command body is not valid JSON for the contract", exception), payloadRef);
            return;
        }
        if (root == null || !root.isObject()) {
            recordAndAcknowledge(new IllegalArgumentException(
                    "refund.command body must be a JSON object"), payloadRef);
            return;
        }

        RefundCommandMessage payload;
        try {
            payload = jsonMapper.treeToValue(root, RefundCommandMessage.class);
        } catch (RuntimeException exception) {
            recordAndAcknowledge(new IllegalArgumentException(
                    "refund.command body does not match the contract", exception), payloadRef);
            return;
        }
        if (payload == null || payload.refundIntentId() == null) {
            recordAndAcknowledge(new IllegalArgumentException(
                    "refund.command body requires refund_intent_id"), payloadRef);
            return;
        }

        try {
            sendRefundToGateway.send(payload.refundIntentId());
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
        return "queue=" + AmqpNames.QUEUE_REFUND_COMMAND
                + (messageId != null ? ";messageId=" + messageId : "");
    }
}
