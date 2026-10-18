package com.seashare.seasharem3.domain.valueobject;

import com.seashare.seasharem3.domain.exception.DomainException;

import java.util.Objects;

/** Clave idempotente de las intenciones (una por intento). Fuente: general-plan §3.3, §3.6 y §11. */
public record IdempotencyKey(String value) {

    public IdempotencyKey {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new DomainException("IdempotencyKey no puede estar vacía");
        }
    }
}
