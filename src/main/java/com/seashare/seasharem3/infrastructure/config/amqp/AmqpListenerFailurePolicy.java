package com.seashare.seasharem3.infrastructure.config.amqp;

import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Política compartida de fallos de los listeners AMQP (contracts/README §4.4):
 * <ul>
 *   <li><b>Error lógico</b> (dato inválido, regla de negocio): el listener llama a
 *       {@link #recordAndAcknowledge}; el fallo queda en {@code operational_failure} y el mensaje se
 *       confirma sin reintentos.</li>
 *   <li><b>Falla transitoria</b> (BD, Flota, pasarela): el listener deja propagar la excepción; los
 *       reintentos los aplica la retry policy del contenedor y, al agotarse, el
 *       {@link OperationalFailureMessageRecoverer} registra y rechaza a la DLQ.</li>
 * </ul>
 * Vive en {@code config.amqp} y no en {@code adapter.in}: los listeners (adaptadores de entrada) no
 * pueden depender de {@code application.port.out} (general-plan §3.4-5) y esta política sí usa el
 * {@link FailureRecorderPort}. Fuente: UC11 plan §Núcleo compartido; general-plan §7.2 y D-08.
 */
@Component
public class AmqpListenerFailurePolicy {

    private static final Logger log = LoggerFactory.getLogger(AmqpListenerFailurePolicy.class);

    private final FailureRecorderPort failureRecorder;

    public AmqpListenerFailurePolicy(FailureRecorderPort failureRecorder) {
        this.failureRecorder = failureRecorder;
    }

    /**
     * Registra un error lógico sin reintentos: el listener lo invoca y retorna normalmente (ack).
     */
    public void recordAndAcknowledge(String useCase, UUID reservationId, Throwable failure, String payloadRef) {
        record(useCase, reservationId, failure, payloadRef);
    }

    /**
     * Registra el fallo y rechaza el mensaje para que no vuelva a la cola de trabajo y termine en su DLQ.
     * Lo invoca el recoverer cuando la retry policy agotó los intentos de una falla transitoria.
     */
    public void recordAndReject(String useCase, UUID reservationId, Throwable failure, String payloadRef) {
        record(useCase, reservationId, failure, payloadRef);
        throw new AmqpRejectAndDontRequeueException(
                "Message rejected after retries exhausted: " + rootCauseMessage(failure), failure);
    }

    private void record(String useCase, UUID reservationId, Throwable failure, String payloadRef) {
        try {
            failureRecorder.record(useCase, reservationId, rootCauseMessage(failure), payloadRef);
        } catch (RuntimeException recordingFailure) {
            // El registro es best effort: nunca puede impedir el ack/nack del mensaje.
            log.error("Could not record operational failure (useCase={}, reservationId={}, payloadRef={})",
                    useCase, reservationId, payloadRef, recordingFailure);
        }
    }

    /** Mensaje de la causa raíz, para que {@code operational_failure.reason} sea útil sin la traza. */
    static String rootCauseMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message != null ? message : root.getClass().getSimpleName();
    }
}
