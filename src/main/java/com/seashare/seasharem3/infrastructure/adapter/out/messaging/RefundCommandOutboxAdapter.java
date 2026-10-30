package com.seashare.seasharem3.infrastructure.adapter.out.messaging;

import com.seashare.seasharem3.application.dto.RefundCommandMessage;
import com.seashare.seasharem3.application.port.out.OutboxPort;
import com.seashare.seasharem3.application.port.out.RefundCommandPublisher;
import com.seashare.seasharem3.infrastructure.config.amqp.AmqpNames;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Objects;
import java.util.UUID;

/** Publica el comando de reembolso en el outbox transaccional (misma transaccion de la intencion). */
@Component
public class RefundCommandOutboxAdapter implements RefundCommandPublisher {

    private final OutboxPort outbox;
    private final JsonMapper jsonMapper;

    public RefundCommandOutboxAdapter(OutboxPort outbox, JsonMapper jsonMapper) {
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
    }

    @Override
    public void publish(UUID refundIntentId) {
        outbox.publish(AmqpNames.EXCHANGE_COMMANDS, AmqpNames.ROUTING_REFUND_COMMAND,
                jsonMapper.writeValueAsString(new RefundCommandMessage(refundIntentId)));
    }
}
