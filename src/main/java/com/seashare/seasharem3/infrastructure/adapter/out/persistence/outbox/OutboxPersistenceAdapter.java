package com.seashare.seasharem3.infrastructure.adapter.out.persistence.outbox;

import com.seashare.seasharem3.application.dto.PendingOutboxMessage;
import com.seashare.seasharem3.application.port.out.OutboxMessageRepository;
import com.seashare.seasharem3.application.port.out.OutboxPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persistencia del outbox: escritura en la transacción del llamador (MANDATORY: el mensaje debe
 * confirmarse junto con el cambio de estado) y lectura/confirmación para el relay.
 * Fuente: UC11 plan T046 (OutboxPersistenceAdapter); general-plan D-08.
 */
@Component
public class OutboxPersistenceAdapter implements OutboxPort, OutboxMessageRepository {

    private final OutboxMessageJpaRepository repository;
    private final Clock clock;

    public OutboxPersistenceAdapter(OutboxMessageJpaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String exchange, String routingKey, String payload) {
        repository.save(new OutboxMessageJpaEntity(UUID.randomUUID(), exchange, routingKey, payload,
                Instant.now(clock)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingOutboxMessage> findPending(int limit) {
        return repository.findByPublishedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, limit)).stream()
                .map(entity -> new PendingOutboxMessage(entity.getId(), entity.getExchange(),
                        entity.getRoutingKey(), entity.getPayload()))
                .toList();
    }

    @Override
    @Transactional
    public void markPublished(UUID id, Instant publishedAt) {
        repository.findById(id).ifPresent(entity -> entity.markPublished(publishedAt));
    }
}
