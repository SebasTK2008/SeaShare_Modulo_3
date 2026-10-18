package com.seashare.seasharem3.domain.valueobject;

import com.seashare.seasharem3.domain.exception.DomainException;

import java.util.Objects;

/**
 * Tarifa base (final, ya ajustada por la tarifa dinámica); rechaza montos ≤ 0.
 * Fuente: UC02 plan T006; general-plan §11.
 */
public record BaseRate(Money amount) {

    public BaseRate {
        Objects.requireNonNull(amount, "amount");
        if (amount.amount().signum() <= 0) {
            throw new DomainException("BaseRate debe ser mayor que 0: " + amount.amount());
        }
    }
}
