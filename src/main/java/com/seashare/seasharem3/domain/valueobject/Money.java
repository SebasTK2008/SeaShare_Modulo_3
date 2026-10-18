package com.seashare.seasharem3.domain.valueobject;

import com.seashare.seasharem3.domain.exception.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Importe monetario con escala interna 4 y salida explícita a 2 decimales HALF_UP.
 * Fuente: contracts/README.md §3.1; general-plan §4 (NUMERIC(18,4)) y D-11.
 */
public record Money(BigDecimal amount) {

    /** Escala interna de cálculo. Fuente: contracts/README.md §3.1. */
    public static final int INTERNAL_SCALE = 4;

    /** Escala de toda salida monetaria (REST, pasarela y registros). Fuente: contracts/README.md §3.1. */
    public static final int OUTPUT_SCALE = 2;

    public Money {
        Objects.requireNonNull(amount, "amount");
        amount = amount.setScale(INTERNAL_SCALE, RoundingMode.HALF_UP);
    }

    /** Suma de importes. Fuente: general-plan §3.6 (operaciones mínimas del VO). */
    public Money add(Money other) {
        Objects.requireNonNull(other, "other");
        return new Money(amount.add(other.amount));
    }

    /** Resta de importes. Fuente: general-plan §3.6 (operaciones mínimas del VO). */
    public Money subtract(Money other) {
        Objects.requireNonNull(other, "other");
        return new Money(amount.subtract(other.amount));
    }

    /** Multiplica el importe por un factor (días, pasajeros o factor dinámico). Fuente: general-plan §3.6 (operaciones mínimas del VO). */
    public Money multiply(BigDecimal factor) {
        Objects.requireNonNull(factor, "factor");
        return new Money(amount.multiply(factor));
    }

    /** Convierte a la salida monetaria canónica de 2 decimales HALF_UP. Fuente: contracts/README.md §3.1. */
    public BigDecimal toOutputAmount() {
        return amount.setScale(OUTPUT_SCALE, RoundingMode.HALF_UP);
    }

    /** Rechaza importes negativos en contextos donde no tienen sentido (p. ej. tarifas). Fuente: general-plan §3.6. */
    public void requireNonNegative() {
        if (amount.signum() < 0) {
            throw new DomainException("El importe no puede ser negativo: " + amount);
        }
    }
}
