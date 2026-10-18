package com.seashare.seasharem3.domain.valueobject;

import com.seashare.seasharem3.domain.exception.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Importe monetario con escala interna 4 y salida explícita a 2 decimales HALF_UP.
 * Fuente: contracts/README.md §3.1; general-plan §4 (NUMERIC(18,4)) y D-11.
 */
public record Money(BigDecimal amount) implements Comparable<Money> {

    /** Escala interna de cálculo. Fuente: contracts/README.md §3.1. */
    public static final int INTERNAL_SCALE = 4;

    /** Escala de toda salida monetaria (REST, pasarela y registros). Fuente: contracts/README.md §3.1. */
    public static final int OUTPUT_SCALE = 2;

    /** Importe neutro aditivo. Fuente: general-plan §3.6 (operaciones mínimas del VO). */
    public static final Money ZERO = new Money(BigDecimal.ZERO);

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public Money {
        Objects.requireNonNull(amount, "amount");
        amount = amount.setScale(INTERNAL_SCALE, RoundingMode.HALF_UP);
    }

    /** Constructor de conveniencia para valores enteros sin fracción. Fuente: general-plan §3.6. */
    public static Money of(long amount) {
        return new Money(BigDecimal.valueOf(amount));
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

    /** Multiplica el importe por un factor decimal (días, pasajeros o factor dinámico). Fuente: general-plan §3.6. */
    public Money multiply(BigDecimal factor) {
        Objects.requireNonNull(factor, "factor");
        return new Money(amount.multiply(factor));
    }

    /** Multiplica el importe por un factor entero. Fuente: general-plan §3.6. */
    public Money multiply(long factor) {
        return new Money(amount.multiply(BigDecimal.valueOf(factor)));
    }

    /**
     * Divide el importe redondeando a la escala interna 4 (HALF_UP); rechaza divisores cero.
     * Fuente: contracts/README.md §3.1 (precisión interna de 4 decimales).
     */
    public Money divide(BigDecimal divisor) {
        Objects.requireNonNull(divisor, "divisor");
        if (divisor.signum() == 0) {
            throw new DomainException("Cannot divide an amount by zero");
        }
        return new Money(amount.divide(divisor, INTERNAL_SCALE, RoundingMode.HALF_UP));
    }

    /**
     * Porcentaje aplicado al importe: {@code amount × pct / 100}, con redondeo interno HALF_UP a 4.
     * Fuente: general-plan §3.6; regla 15 de UC02 (escala 4 sin redondeo final).
     */
    public Money percentageOf(Percentage percentage) {
        Objects.requireNonNull(percentage, "percentage");
        return new Money(amount.multiply(percentage.value()).divide(HUNDRED, INTERNAL_SCALE, RoundingMode.HALF_UP));
    }

    /**
     * Importe incrementado por un porcentaje: {@code amount + amount × pct / 100}.
     * Fuente: UC02 plan T011 (fórmula {@code base × (1 + %/100)} coincide con el plan).
     */
    public Money increaseBy(Percentage percentage) {
        return add(percentageOf(percentage));
    }

    /** Negación aritmética del importe. Fuente: general-plan §3.6. */
    public Money negate() {
        return new Money(amount.negate());
    }

    /** Valor absoluto del importe. Fuente: general-plan §3.6. */
    public Money abs() {
        return new Money(amount.abs());
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isGreaterThan(Money other) {
        Objects.requireNonNull(other, "other");
        return amount.compareTo(other.amount) > 0;
    }

    public boolean isGreaterThanOrEqualTo(Money other) {
        Objects.requireNonNull(other, "other");
        return amount.compareTo(other.amount) >= 0;
    }

    public boolean isLessThan(Money other) {
        Objects.requireNonNull(other, "other");
        return amount.compareTo(other.amount) < 0;
    }

    public boolean isLessThanOrEqualTo(Money other) {
        Objects.requireNonNull(other, "other");
        return amount.compareTo(other.amount) <= 0;
    }

    /** Menor de dos importes. Fuente: general-plan §3.6. */
    public Money min(Money other) {
        Objects.requireNonNull(other, "other");
        return isLessThanOrEqualTo(other) ? this : other;
    }

    /** Mayor de dos importes. Fuente: general-plan §3.6. */
    public Money max(Money other) {
        Objects.requireNonNull(other, "other");
        return isGreaterThanOrEqualTo(other) ? this : other;
    }

    /** Convierte a la salida monetaria canónica de 2 decimales HALF_UP. Fuente: contracts/README.md §3.1. */
    public BigDecimal toOutputAmount() {
        return amount.setScale(OUTPUT_SCALE, RoundingMode.HALF_UP);
    }

    /** Rechaza importes negativos en contextos donde no tienen sentido (p. ej. tarifas). Fuente: general-plan §3.6. */
    public void requireNonNegative() {
        if (isNegative()) {
            throw new DomainException("Amount cannot be negative: " + amount);
        }
    }

    /** Rechaza importes ≤ 0 en contextos donde se exige estrictamente positivo. Fuente: general-plan §3.6. */
    public void requirePositive() {
        if (!isPositive()) {
            throw new DomainException("Amount must be greater than zero: " + amount);
        }
    }

    @Override
    public int compareTo(Money other) {
        Objects.requireNonNull(other, "other");
        return amount.compareTo(other.amount);
    }
}
