package com.seashare.seasharem3.infrastructure.adapter.out.messaging;

import com.seashare.seasharem3.application.port.out.OutboxPort;
import com.seashare.seasharem3.application.port.out.RefundCommandPublisher;
import com.seashare.seasharem3.infrastructure.config.amqp.AmqpNames;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.Objects;
import java.util.UUID;
import java.util.Map;

/**
 * Publica el comando de reembolso en el outbox transaccional.
 *
 * <p>Abre una transacción si el llamador no la tiene (conciliación) y se une a la existente cuando
 * la hay: el puerto del outbox exige {@code Propagation.MANDATORY}.</p>
 */
@Component
public class RefundCommandOutboxAdapter implements RefundCommandPublisher {

    private final OutboxPort outbox;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transactions;

    public RefundCommandOutboxAdapter(OutboxPort outbox, JsonMapper jsonMapper,
            TransactionTemplate transactions) {
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    @Override
    public void publish(UUID refundIntentId) {
        String payload = jsonMapper.writeValueAsString(Map.of("refund_intent_id", refundIntentId));
        transactions.executeWithoutResult(status ->
                outbox.publish(AmqpNames.EXCHANGE_COMMANDS, AmqpNames.ROUTING_REFUND_COMMAND, payload));
    }
}
