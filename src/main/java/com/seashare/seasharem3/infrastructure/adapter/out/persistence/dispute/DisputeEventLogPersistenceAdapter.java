package com.seashare.seasharem3.infrastructure.adapter.out.persistence.dispute;

import com.seashare.seasharem3.application.port.out.DisputeEventLogRepository;
import com.seashare.seasharem3.domain.model.DisputeEvent;
import com.seashare.seasharem3.domain.valueobject.DisputeId;
import com.seashare.seasharem3.domain.valueobject.DisputeStatus;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/** Adaptador JPA de {@code dispute_event_log}: reclamo idempotente y marca de procesado. */
@Component
public class DisputeEventLogPersistenceAdapter implements DisputeEventLogRepository {

    private final DisputeEventLogJpaRepository repository;

    public DisputeEventLogPersistenceAdapter(DisputeEventLogJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public boolean insertIfAbsent(DisputeEvent event) {
        return repository.insertIfAbsent(event.id(), event.reservationId().value(), event.disputeId().value(),
                event.eventKey(), event.status().name(),
                OffsetDateTime.ofInstant(event.statusChangedAt(), ZoneOffset.UTC)) == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DisputeEvent> findByKey(ReservationId reservationId, DisputeId disputeId, String eventKey) {
        return repository.findByReservationIdAndDisputeIdAndEventKey(reservationId.value(), disputeId.value(), eventKey)
                .map(DisputeEventLogPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public boolean markProcessed(UUID eventId, Instant processedAt) {
        return repository.markProcessed(eventId, processedAt) == 1;
    }

    private static DisputeEvent toDomain(DisputeEventLogJpaEntity entity) {
        return new DisputeEvent(entity.getId(), new ReservationId(entity.getReservationId()),
                new DisputeId(entity.getDisputeId()), entity.getEventKey(), DisputeStatus.valueOf(entity.getStatus()),
                entity.getStatusChangedAt(), entity.getProcessedAt());
    }
}
