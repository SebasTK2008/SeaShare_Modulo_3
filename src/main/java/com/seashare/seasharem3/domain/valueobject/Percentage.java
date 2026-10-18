package com.seashare.seasharem3.domain.valueobject;

import com.seashare.seasharem3.domain.exception.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Porcentaje entre 0 y 100 inclusive, con escala interna 4.
 * Fuente: contracts/README.md §3.1; general-plan §3.6; UC11 plan §Reglas 2 (0–100).
 */
public record Percentage(BigDecimal value) {

    /** Límite inferior permitido (inclusive). Fuente: UC11 plan §Reglas 2. */
    public static final BigDecimal MIN = BigDecimal.ZERO;

    /** Límite superior permitido (inclusive). Fuente: UC11 plan §Reglas 2. */
    public static final BigDecimal MAX = new BigDecimal("100");

    /** Porcentaje nulo. Fuente: general-plan §3.6 (operaciones mínimas del VO). */
    public static final Percentage ZERO = new Percentage(BigDecimal.ZERO);

    /** Porcentaje máximo (100 %). Fuente: general-plan §3.6 (operaciones mínimas del VO). */
    public static final Percentage HUNDRED = new Percentage(MAX);

    public Percentage {
        Objects.requireNonNull(value, "value");
        value = value.setScale(Money.INTERNAL_SCALE, RoundingMode.HALF_UP);
        if (value.compareTo(MIN) < 0 || value.compareTo(MAX) > 0) {
            throw new DomainException("Percentage fuera del rango 0–100: " + value);
        }
    }

    /** {@code true} si el porcentaje es 0 %. Fuente: general-plan §3.6. */
    public boolean isZero() {
        return value.signum() == 0;
    }
}
