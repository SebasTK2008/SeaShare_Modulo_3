package com.seashare.seasharem3.infrastructure.adapter.out.persistence.failure;

import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Registra el fallo en {@code operational_failure} con transacción independiente (REQUIRES_NEW),
 * para que sobreviva al rollback de la transacción del llamador.
 * Fuente: UC11 plan §Núcleo compartido (FailureRecorderPersistenceAdapter) y general-plan §4.
 */
@Component
public class OperationalFailurePersistenceAdapter implements FailureRecorderPort {

    private final OperationalFailureJpaRepository repository;
    private final Clock clock;

    public OperationalFailurePersistenceAdapter(OperationalFailureJpaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String useCase, UUID reservationId, String reason, String payloadRef) {
        repository.save(new OperationalFailureJpaEntity(UUID.randomUUID(), useCase, reservationId, reason,
                payloadRef, Instant.now(clock), false));
    }
}
