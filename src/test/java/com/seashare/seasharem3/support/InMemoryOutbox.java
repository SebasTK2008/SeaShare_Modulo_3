package com.seashare.seasharem3.support;

import com.seashare.seasharem3.application.dto.PendingOutboxMessage;
import com.seashare.seasharem3.application.port.out.OutboxMessageRepository;
import com.seashare.seasharem3.application.port.out.OutboxPort;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fake en memoria del outbox ({@link OutboxPort} + {@link OutboxMessageRepository}) con orden de
 * inserción. [CONV] núcleo compartido para las pruebas de los UC.
 */
public class InMemoryOutbox implements OutboxPort, OutboxMessageRepository {

    private final Map<UUID, Entry> messages = new LinkedHashMap<>();

    @Override
    public void publish(String exchange, String routingKey, String payload) {
        UUID id = UUID.randomUUID();
        messages.put(id, new Entry(id, exchange, routingKey, payload, null));
    }

    @Override
    public List<PendingOutboxMessage> findPending(int limit) {
        return messages.values().stream()
                .filter(entry -> entry.publishedAt() == null)
                .limit(limit)
                .map(entry -> new PendingOutboxMessage(entry.id(), entry.exchange(), entry.routingKey(),
                        entry.payload()))
                .toList();
    }

    @Override
    public void markPublished(UUID id, Instant publishedAt) {
        Entry entry = messages.get(id);
        if (entry != null) {
            messages.put(id, new Entry(entry.id(), entry.exchange(), entry.routingKey(), entry.payload(),
                    publishedAt));
        }
    }

    public List<PendingOutboxMessage> pending() {
        return findPending(Integer.MAX_VALUE);
    }

    public int pendingCount() {
        return (int) messages.values().stream().filter(entry -> entry.publishedAt() == null).count();
    }

    public int publishedCount() {
        return (int) messages.values().stream().filter(entry -> entry.publishedAt() != null).count();
    }

    private record Entry(UUID id, String exchange, String routingKey, String payload, Instant publishedAt) {
    }
}
