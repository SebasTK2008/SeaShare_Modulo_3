package com.seashare.seasharem3.infrastructure.adapter.out.messaging;

import com.seashare.seasharem3.application.port.out.MessageBrokerPort;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publicación de payloads JSON en RabbitMQ con acuse de publicación (publisher confirms) y detección
 * de mensajes no enrutables (mandatory + returns). Requiere {@code spring.rabbitmq.publisher-confirm-type=correlated}.
 * Fuente: general-plan §3.5 y §7.2; UC11 plan T046 (relay del outbox). [CONV] núcleo compartido.
 */
@Component
public class RabbitMessagePublisher implements MessageBrokerPort {

    private final RabbitTemplate rabbitTemplate;
    private final Duration confirmTimeout;

    public RabbitMessagePublisher(RabbitTemplate rabbitTemplate,
            @Value("${seashare.outbox.publish-confirm-timeout:5s}") Duration confirmTimeout) {
        this.rabbitTemplate = rabbitTemplate;
        this.confirmTimeout = confirmTimeout;
    }

    @Override
    public void publish(String exchange, String routingKey, String payload) {
        String messageId = UUID.randomUUID().toString();
        Message message = MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setDeliveryMode(MessageProperties.DEFAULT_DELIVERY_MODE)
                .setMessageId(messageId)
                .build();
        CorrelationData correlationData = new CorrelationData(messageId);
        rabbitTemplate.send(exchange, routingKey, message, correlationData);
        try {
            CorrelationData.Confirm confirm = correlationData.getFuture().get(confirmTimeout.toMillis(),
                    TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new AmqpException("La publicación no fue confirmada por el broker: " + confirm.getReason());
            }
            if (correlationData.getReturned() != null) {
                throw new AmqpException("Mensaje no enrutable: " + correlationData.getReturned().getReplyText());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Publicación interrumpida", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new AmqpException("Sin confirmación del broker en " + confirmTimeout, exception);
        }
    }
}
