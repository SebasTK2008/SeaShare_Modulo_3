package com.seashare.seasharem3.application.port.out;

import java.util.UUID;

/** Firma publicada en UC11 plan §Núcleo compartido: registra el fallo con transacción independiente (REQUIRES_NEW). */
public interface FailureRecorderPort {

    void record(String useCase, UUID reservationId, String reason, String payloadRef);
}
