package com.seashare.seasharem3.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

/** Identificador de reserva (wrapper de UUID v4). Fuente: general-plan §3.6 y §4. */
public record ReservationId(UUID value) {

    public ReservationId {
        Objects.requireNonNull(value, "value");
    }
}
