package com.seashare.seasharem3.infrastructure.adapter.out.messaging;

import com.seashare.seasharem3.application.dto.SettlementCommandMessage;
import com.seashare.seasharem3.application.port.out.OutboxPort;
import com.seashare.seasharem3.application.port.out.SettlementCommandPublisher;
import com.seashare.seasharem3.infrastructure.config.amqp.AmqpNames;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Objects;
import java.util.UUID;

/** Publica el comando de liquidacion en el outbox transaccional. */
@Component
public class SettlementCommandOutboxAdapter implements SettlementCommandPublisher {

    private final OutboxPort outbox;
    private final JsonMapper jsonMapper;

    public SettlementCommandOutboxAdapter(OutboxPort outbox, JsonMapper jsonMapper) {
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
    }

    @Override
    public void publish(UUID settlementIntentId) {
        outbox.publish(AmqpNames.EXCHANGE_COMMANDS, AmqpNames.ROUTING_SETTLEMENT_COMMAND,
                jsonMapper.writeValueAsString(new SettlementCommandMessage(settlementIntentId)));
    }
}
