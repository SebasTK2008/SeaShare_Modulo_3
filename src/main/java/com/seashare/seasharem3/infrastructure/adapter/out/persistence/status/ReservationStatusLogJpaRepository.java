package com.seashare.seasharem3.infrastructure.adapter.out.persistence.status;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/** Repositorio Spring Data de {@code reservation_status_log}. */
public interface ReservationStatusLogJpaRepository extends JpaRepository<ReservationStatusLogJpaEntity, UUID> {

    /**
     * Reclama la operación si su clave de negocio no existe. El {@code ON CONFLICT DO NOTHING} evita
     * lanzar una violación de unicidad dentro de la transacción y hace atómico el reclamo.
     */
    @Modifying
    @Query(value = "INSERT INTO reservation_status_log "
            + "(id, reservation_id, status, operation_type, idempotency_key, status_changed_at, processed_at, outcome) "
            + "VALUES (:id, :reservationId, :status, :operationType, :idempotencyKey, :statusChangedAt, NULL, NULL) "
            + "ON CONFLICT (reservation_id, status, operation_type, idempotency_key) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("reservationId") UUID reservationId,
            @Param("status") String status, @Param("operationType") String operationType,
            @Param("idempotencyKey") String idempotencyKey, @Param("statusChangedAt") OffsetDateTime statusChangedAt);

    @Modifying
    @Query("update ReservationStatusLogJpaEntity l set l.processedAt = :processedAt, l.outcome = :outcome "
            + "where l.id = :id")
    int markProcessed(@Param("id") UUID id, @Param("processedAt") Instant processedAt,
            @Param("outcome") String outcome);

    Optional<ReservationStatusLogJpaEntity> findByReservationIdAndStatusAndOperationTypeAndIdempotencyKey(
            UUID reservationId, String status, String operationType, String idempotencyKey);
}
