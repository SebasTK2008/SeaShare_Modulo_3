package com.seashare.seasharem3.infrastructure.adapter.out.persistence.dispute;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/** Repositorio Spring Data de {@code dispute_event_log}. */
public interface DisputeEventLogJpaRepository extends JpaRepository<DisputeEventLogJpaEntity, UUID> {

    /**
     * Inserta el evento si su clave de negocio no existe. El {@code ON CONFLICT DO NOTHING} evita
     * lanzar una violacion de unicidad dentro de la transaccion y marca la reclamacion como atomica.
     */
    @Modifying
    @Query(value = "INSERT INTO dispute_event_log "
            + "(id, reservation_id, dispute_id, event_key, status, status_changed_at, processed_at) "
            + "VALUES (:id, :reservationId, :disputeId, :eventKey, :status, :statusChangedAt, NULL) "
            + "ON CONFLICT (reservation_id, dispute_id, event_key) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("reservationId") UUID reservationId,
            @Param("disputeId") UUID disputeId, @Param("eventKey") String eventKey,
            @Param("status") String status, @Param("statusChangedAt") OffsetDateTime statusChangedAt);

    Optional<DisputeEventLogJpaEntity> findByReservationIdAndDisputeIdAndEventKey(UUID reservationId,
            UUID disputeId, String eventKey);

    @Modifying
    @Query("update DisputeEventLogJpaEntity e set e.processedAt = :processedAt where e.id = :id")
    int markProcessed(@Param("id") UUID id, @Param("processedAt") Instant processedAt);
}
