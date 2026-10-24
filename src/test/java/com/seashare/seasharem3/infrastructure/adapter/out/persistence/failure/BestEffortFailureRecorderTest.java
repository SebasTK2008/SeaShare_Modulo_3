package com.seashare.seasharem3.infrastructure.adapter.out.persistence.failure;

import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class BestEffortFailureRecorderTest {

    @Test
    void delegatesSuccessfulRecording() {
        AtomicReference<String> recorded = new AtomicReference<>();
        FailureRecorderPort delegate = (useCase, reservationId, reason, payloadRef) ->
                recorded.set(useCase + "|" + reason + "|" + payloadRef);

        new BestEffortFailureRecorder(delegate).record("UC02", null, "FLEET_UNAVAILABLE",
                "evaluatedDate=2026-10-03;boatIds=[boat-1]");

        assertThat(recorded).hasValue("UC02|FLEET_UNAVAILABLE|evaluatedDate=2026-10-03;boatIds=[boat-1]");
    }

    @Test
    void doesNotPropagateRecorderFailures() {
        FailureRecorderPort delegate = (useCase, reservationId, reason, payloadRef) -> {
            throw new IllegalStateException("operational_failure is unavailable");
        };

        assertThatCode(() -> new BestEffortFailureRecorder(delegate).record("UC02",
                UUID.fromString("3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01"),
                "FLEET_UNAVAILABLE", "evaluatedDate=2026-10-03;boatIds=[boat-1]"))
                .doesNotThrowAnyException();
    }
}
