package com.seashare.seasharem3.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

/** Identificador de propietario (wrapper de UUID v4, dato plano sin validación de identidad). Fuente: general-plan §3.6 y D-21. */
public record OwnerId(UUID value) {

    public OwnerId {
        Objects.requireNonNull(value, "value");
    }
}
