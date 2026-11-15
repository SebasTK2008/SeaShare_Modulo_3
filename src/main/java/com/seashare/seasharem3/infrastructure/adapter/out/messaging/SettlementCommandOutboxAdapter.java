package com.seashare.seasharem3.infrastructure.adapter.out.messaging;

import com.seashare.seasharem3.application.port.out.OutboxPort;
import com.seashare.seasharem3.application.port.out.SettlementCommandPublisher;
import com.seashare.seasharem3.infrastructure.config.amqp.AmqpNames;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.Objects;
import java.util.UUID;
import java.util.Map;

/** Publica el comando de liquidación en el outbox transaccional (abre transacción si no hay una). */
@Component
public class SettlementCommandOutboxAdapter implements SettlementCommandPublisher {

    private final OutboxPort outbox;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transactions;

    public SettlementCommandOutboxAdapter(OutboxPort outbox, JsonMapper jsonMapper,
            TransactionTemplate transactions) {
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    @Override
    public void publish(UUID settlementIntentId) {
        String payload = jsonMapper.writeValueAsString(Map.of("settlement_intent_id", settlementIntentId));
        transactions.executeWithoutResult(status ->
                outbox.publish(AmqpNames.EXCHANGE_COMMANDS, AmqpNames.ROUTING_SETTLEMENT_COMMAND, payload));
    }
}
