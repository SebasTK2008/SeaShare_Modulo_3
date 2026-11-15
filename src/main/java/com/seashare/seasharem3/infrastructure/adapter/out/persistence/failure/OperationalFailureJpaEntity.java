package com.seashare.seasharem3.infrastructure.adapter.out.persistence.failure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Mapeo de {@code operational_failure} (general-plan §4; migración V1). No sale del adaptador. */
@Entity
@Table(name = "operational_failure")
public class OperationalFailureJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "use_case", nullable = false, length = 100)
    private String useCase;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "payload_ref")
    private String payloadRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved", nullable = false)
    private boolean resolved;

    protected OperationalFailureJpaEntity() {
    }

    public OperationalFailureJpaEntity(UUID id, String useCase, UUID reservationId, String reason,
            String payloadRef, Instant createdAt, boolean resolved) {
        this.id = id;
        this.useCase = useCase;
        this.reservationId = reservationId;
        this.reason = reason;
        this.payloadRef = payloadRef;
        this.createdAt = createdAt;
        this.resolved = resolved;
    }

    public UUID getId() {
        return id;
    }

    public String getUseCase() {
        return useCase;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public String getReason() {
        return reason;
    }

    public String getPayloadRef() {
        return payloadRef;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isResolved() {
        return resolved;
    }
}
