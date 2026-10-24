package com.seashare.seasharem3.infrastructure.adapter.out.persistence.failure;

import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Decorador best-effort del registro de fallos operativos.
 *
 * <p>Envuelve al adaptador real y evita que un fallo al escribir en {@code operational_failure}
 * enmascare la excepción original del caso de uso. El decorador vive en infraestructura porque
 * {@code application} no puede depender de SLF4J (ArchitectureTest, regla 2). Al rodear el proxy
 * transaccional del adaptador también captura los fallos producidos al confirmar la transacción
 * {@code REQUIRES_NEW}.</p>
 *
 * <p>Fuente: UC02 hardening R1 (2026-10-24).</p>
 */
@Component
@Primary
public class BestEffortFailureRecorder implements FailureRecorderPort {

    private static final Logger log = LoggerFactory.getLogger(BestEffortFailureRecorder.class);

    private final FailureRecorderPort delegate;

    public BestEffortFailureRecorder(
            @Qualifier("operationalFailurePersistenceAdapter") FailureRecorderPort delegate) {
        this.delegate = delegate;
    }

    @Override
    public void record(String useCase, UUID reservationId, String reason, String payloadRef) {
        try {
            delegate.record(useCase, reservationId, reason, payloadRef);
        } catch (RuntimeException exception) {
            log.error("Could not persist operational failure (useCase={}, reservationId={}, reason={}, payloadRef={})",
                    useCase, reservationId, reason, payloadRef, exception);
        }
    }
}
