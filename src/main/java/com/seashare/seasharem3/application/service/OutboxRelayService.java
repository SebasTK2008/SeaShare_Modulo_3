package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.PendingOutboxMessage;
import com.seashare.seasharem3.application.port.in.RelayOutboxUseCase;
import com.seashare.seasharem3.application.port.out.MessageBrokerPort;
import com.seashare.seasharem3.application.port.out.OutboxMessageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

/**
 * Publica los mensajes pendientes del outbox en el broker y los marca publicados (entrega
 * at-least-once: si falla el marcado, se republica en el siguiente ciclo; los consumidores
 * deduplican por clave idempotente).
 * Fuente: UC11 plan T046 (OutboxRelayJob del núcleo compartido); general-plan D-08.
 */
@Service
public class OutboxRelayService implements RelayOutboxUseCase {

    private final OutboxMessageRepository outboxMessageRepository;
    private final MessageBrokerPort messageBrokerPort;
    private final Clock clock;
    private final int batchSize;

    public OutboxRelayService(OutboxMessageRepository outboxMessageRepository, MessageBrokerPort messageBrokerPort,
            Clock clock, @Value("${seashare.outbox.batch-size:100}") int batchSize) {
        this.outboxMessageRepository = outboxMessageRepository;
        this.messageBrokerPort = messageBrokerPort;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Override
    public int relay() {
        List<PendingOutboxMessage> pending = outboxMessageRepository.findPending(batchSize);
        int published = 0;
        for (PendingOutboxMessage message : pending) {
            messageBrokerPort.publish(message.exchange(), message.routingKey(), message.payload());
            outboxMessageRepository.markPublished(message.id(), clock.instant());
            published++;
        }
        return published;
    }
}
