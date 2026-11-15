package com.seashare.seasharem3.infrastructure.adapter.out.persistence.status;

import com.seashare.seasharem3.application.port.out.ReservationStatusLogRepository;
import com.seashare.seasharem3.domain.valueobject.FinancialOperationType;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.domain.valueobject.ReservationStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

/**
 * Adaptador JPA de {@code reservation_status_log}: reclamo idempotente de la operación y marca de
 * procesado. Fuente: UC07 plan T007 y §Reglas 7-8.
 */
@Component
public class ReservationStatusLogPersistenceAdapter implements ReservationStatusLogRepository {

    private final ReservationStatusLogJpaRepository repository;

    public ReservationStatusLogPersistenceAdapter(ReservationStatusLogJpaRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    @Transactional
    public boolean insertIfAbsent(UUID id, ReservationId reservationId, ReservationStatus status,
            FinancialOperationType operationType, IdempotencyKey idempotencyKey, Instant statusChangedAt) {
        return repository.insertIfAbsent(id, reservationId.value(), status.name(), operationType.name(),
                idempotencyKey.value(), OffsetDateTime.ofInstant(statusChangedAt, ZoneOffset.UTC)) == 1;
    }

    @Override
    @Transactional
    public boolean markProcessed(UUID id, Instant processedAt, String outcome) {
        return repository.markProcessed(id, processedAt, outcome) == 1;
    }
}
