package com.seashare.seasharem3.support;

import com.seashare.seasharem3.application.port.out.DisputeEventLogRepository;
import com.seashare.seasharem3.domain.model.DisputeEvent;
import com.seashare.seasharem3.domain.valueobject.DisputeId;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Fake en memoria de {@link DisputeEventLogRepository} para pruebas de aplicacion y aceptacion. */
public class InMemoryDisputeEventLogRepository implements DisputeEventLogRepository {

    private final Map<String, DisputeEvent> events = new ConcurrentHashMap<>();

    @Override
    public boolean insertIfAbsent(DisputeEvent event) {
        return events.putIfAbsent(key(event.reservationId(), event.disputeId(), event.eventKey()), event) == null;
    }

    @Override
    public Optional<DisputeEvent> findByKey(ReservationId reservationId, DisputeId disputeId, String eventKey) {
        return Optional.ofNullable(events.get(key(reservationId, disputeId, eventKey)));
    }

    @Override
    public boolean markProcessed(UUID eventId, Instant processedAt) {
        for (Map.Entry<String, DisputeEvent> entry : events.entrySet()) {
            if (entry.getValue().id().equals(eventId)) {
                events.put(entry.getKey(), entry.getValue().markProcessed(processedAt));
                return true;
            }
        }
        return false;
    }

    public List<DisputeEvent> events() {
        return List.copyOf(events.values());
    }

    public void clear() {
        events.clear();
    }

    private static String key(ReservationId reservationId, DisputeId disputeId, String eventKey) {
        return reservationId.value() + "|" + disputeId.value() + "|" + eventKey;
    }
}
