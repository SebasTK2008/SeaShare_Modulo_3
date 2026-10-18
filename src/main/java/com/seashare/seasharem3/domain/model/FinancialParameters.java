package com.seashare.seasharem3.domain.model;

import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Parámetros financieros globales versionados append-only (una versión por guardado);
 * la vigente es la de mayor {@code version_number}.
 * Fuente: general-plan §4; UC11 plan T002/T012 y contrato rest/UC11-guardar-parametros-financieros.md.
 * Inmutable: nunca se modifica una versión ya creada.
 */
public final class FinancialParameters {

    private final UUID id;
    private final long versionNumber;
    private final Percentage commissionPct;
    private final Money insuranceFeePerPassenger;
    private final Percentage weekendIncreasePct;
    private final Percentage highSeasonIncreasePct;
    private final String createdBy;
    private final Instant createdAt;

    private FinancialParameters(UUID id, long versionNumber, Percentage commissionPct,
            Money insuranceFeePerPassenger, Percentage weekendIncreasePct, Percentage highSeasonIncreasePct,
            String createdBy, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.versionNumber = versionNumber;
        this.commissionPct = Objects.requireNonNull(commissionPct, "commissionPct");
        this.insuranceFeePerPassenger = Objects.requireNonNull(insuranceFeePerPassenger, "insuranceFeePerPassenger");
        this.weekendIncreasePct = Objects.requireNonNull(weekendIncreasePct, "weekendIncreasePct");
        this.highSeasonIncreasePct = Objects.requireNonNull(highSeasonIncreasePct, "highSeasonIncreasePct");
        this.createdBy = createdBy;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (versionNumber < 1) {
            throw new DomainException("version_number debe ser al menos 1: " + versionNumber);
        }
        insuranceFeePerPassenger.requireNonNegative();
    }

    /** Crea una versión nueva (append-only). Fuente: UC11 plan T002; DDL V2. */
    public static FinancialParameters create(UUID id, long versionNumber, Percentage commissionPct,
            Money insuranceFeePerPassenger, Percentage weekendIncreasePct, Percentage highSeasonIncreasePct,
            String createdBy, Instant createdAt) {
        return new FinancialParameters(id, versionNumber, commissionPct, insuranceFeePerPassenger, weekendIncreasePct,
                highSeasonIncreasePct, createdBy, createdAt);
    }

    public UUID id() {
        return id;
    }

    public long versionNumber() {
        return versionNumber;
    }

    public Percentage commissionPct() {
        return commissionPct;
    }

    public Money insuranceFeePerPassenger() {
        return insuranceFeePerPassenger;
    }

    public Percentage weekendIncreasePct() {
        return weekendIncreasePct;
    }

    public Percentage highSeasonIncreasePct() {
        return highSeasonIncreasePct;
    }

    /** Dato plano opcional ([SPEC]); {@code null} si no se recibió. Fuente: general-plan §4. */
    public String createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FinancialParameters that)) {
            return false;
        }
        return versionNumber == that.versionNumber && id.equals(that.id) && commissionPct.equals(that.commissionPct)
                && insuranceFeePerPassenger.equals(that.insuranceFeePerPassenger)
                && weekendIncreasePct.equals(that.weekendIncreasePct)
                && highSeasonIncreasePct.equals(that.highSeasonIncreasePct)
                && Objects.equals(createdBy, that.createdBy) && createdAt.equals(that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, versionNumber, commissionPct, insuranceFeePerPassenger, weekendIncreasePct,
                highSeasonIncreasePct, createdBy, createdAt);
    }

    @Override
    public String toString() {
        return "FinancialParameters[id=" + id + ", versionNumber=" + versionNumber + "]";
    }
}
