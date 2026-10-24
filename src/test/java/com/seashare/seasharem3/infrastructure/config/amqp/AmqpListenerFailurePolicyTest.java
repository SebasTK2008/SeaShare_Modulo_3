package com.seashare.seasharem3.infrastructure.config.amqp;

import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.support.InMemoryFailureRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La política del núcleo registra el fallo con la causa raíz y decide el desenlace del mensaje:
 * ack para errores lógicos y rechazo a la DLQ para fallas transitorias agotadas.
 * Fuente: contracts/README §4.4. [CONV] núcleo compartido.
 */
class AmqpListenerFailurePolicyTest {

    private static final UUID RESERVATION_ID = UUID.fromString("1e6d3f1a-4b2c-4d5e-8f7a-9b0c1d2e3f40");

    private final InMemoryFailureRecorder recorder = new InMemoryFailureRecorder();
    private final AmqpListenerFailurePolicy policy = new AmqpListenerFailurePolicy(recorder);

    @Test
    void recordAndAcknowledgeStoresFailureWithRootCauseMessageWithoutThrowing() {
        Throwable failure = new IllegalStateException("validation failed", new IllegalArgumentException("bad period"));

        assertThatCode(() -> policy.recordAndAcknowledge("UC03", RESERVATION_ID, failure, "queue=finance.reservation-info.v1"))
                .doesNotThrowAnyException();

        assertThat(recorder.failures()).singleElement().satisfies(recorded -> {
            assertThat(recorded.useCase()).isEqualTo("UC03");
            assertThat(recorded.reservationId()).isEqualTo(RESERVATION_ID);
            assertThat(recorded.reason()).isEqualTo("bad period");
            assertThat(recorded.payloadRef()).isEqualTo("queue=finance.reservation-info.v1");
        });
    }

    @Test
    void recordAndRejectStoresFailureAndThrowsAmqpRejectAndDontRequeue() {
        Throwable failure = new RuntimeException("database unavailable");

        assertThatThrownBy(() -> policy.recordAndReject("UC05", RESERVATION_ID, failure, "queue=finance.charge-command.v1"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("database unavailable");

        assertThat(recorder.failures()).singleElement().satisfies(recorded -> {
            assertThat(recorded.useCase()).isEqualTo("UC05");
            assertThat(recorded.reason()).isEqualTo("database unavailable");
        });
    }

    @Test
    void recordingFailureDoesNotPreventMessageRejection() {
        FailureRecorderPort brokenRecorder = (useCase, reservationId, reason, payloadRef) -> {
            throw new IllegalStateException("operational_failure is unavailable too");
        };
        AmqpListenerFailurePolicy brokenPolicy = new AmqpListenerFailurePolicy(brokenRecorder);

        assertThatThrownBy(() -> brokenPolicy.recordAndReject("UC09", RESERVATION_ID,
                new RuntimeException("gateway timeout"), "queue=finance.refund-command.v1"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("gateway timeout");
    }

    @Test
    void rootCauseMessageSkipsWrapperMessagesAndFallsBackToClassName() {
        assertThat(AmqpListenerFailurePolicy.rootCauseMessage(
                new IllegalStateException("wrapper", new RuntimeException("base cause"))))
                .isEqualTo("base cause");
        assertThat(AmqpListenerFailurePolicy.rootCauseMessage(new RuntimeException((String) null)))
                .isEqualTo("RuntimeException");
    }
}
