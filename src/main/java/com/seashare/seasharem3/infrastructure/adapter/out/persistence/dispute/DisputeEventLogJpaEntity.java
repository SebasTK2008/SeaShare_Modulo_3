package com.seashare.seasharem3.infrastructure.adapter.out.persistence.dispute;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Mapeo de {@code dispute_event_log} (UC08). La entidad no sale del paquete de persistencia. */
@Entity
@Table(name = "dispute_event_log")
public class DisputeEventLogJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "dispute_id", nullable = false)
    private UUID disputeId;

    @Column(name = "event_key", nullable = false, length = 128)
    private String eventKey;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected DisputeEventLogJpaEntity() {
    }

    UUID getId() {
        return id;
    }

    UUID getReservationId() {
        return reservationId;
    }

    UUID getDisputeId() {
        return disputeId;
    }

    String getEventKey() {
        return eventKey;
    }

    String getStatus() {
        return status;
    }

    Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    Instant getProcessedAt() {
        return processedAt;
    }
}
