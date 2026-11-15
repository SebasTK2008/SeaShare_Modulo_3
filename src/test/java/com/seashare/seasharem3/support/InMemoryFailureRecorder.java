package com.seashare.seasharem3.support;

import com.seashare.seasharem3.application.port.out.FailureRecorderPort;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fake en memoria de {@link FailureRecorderPort}. [CONV] núcleo compartido para las pruebas de los UC.
 */
public class InMemoryFailureRecorder implements FailureRecorderPort {

    private final List<RecordedFailure> failures = new ArrayList<>();

    @Override
    public void record(String useCase, UUID reservationId, String reason, String payloadRef) {
        failures.add(new RecordedFailure(useCase, reservationId, reason, payloadRef));
    }

    public List<RecordedFailure> failures() {
        return List.copyOf(failures);
    }

    public void clear() {
        failures.clear();
    }

    public record RecordedFailure(String useCase, UUID reservationId, String reason, String payloadRef) {
    }
}
