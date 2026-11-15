package com.seashare.seasharem3.infrastructure.adapter.out.persistence.status;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Mapeo de {@code reservation_status_log} (UC07). La entidad no sale del paquete de persistencia. */
@Entity
@Table(name = "reservation_status_log")
public class ReservationStatusLogJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "operation_type", nullable = false, length = 32)
    private String operationType;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "outcome")
    private String outcome;

    protected ReservationStatusLogJpaEntity() {
    }

    UUID getId() {
        return id;
    }

    UUID getReservationId() {
        return reservationId;
    }

    String getStatus() {
        return status;
    }

    String getOperationType() {
        return operationType;
    }

    String getIdempotencyKey() {
        return idempotencyKey;
    }

    Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    Instant getProcessedAt() {
        return processedAt;
    }

    String getOutcome() {
        return outcome;
    }
}
