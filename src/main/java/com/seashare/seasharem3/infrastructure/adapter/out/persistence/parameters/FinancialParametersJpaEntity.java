package com.seashare.seasharem3.infrastructure.adapter.out.persistence.parameters;

import com.seashare.seasharem3.domain.model.FinancialParameters;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Mapeo de {@code financial_parameters} (general-plan §4; migración V1). La tabla es append-only por
 * trigger: este mapeo solo inserta versiones nuevas. No sale del adaptador.
 */
@Entity
@Table(name = "financial_parameters")
public class FinancialParametersJpaEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /**
     * Marca de entidad nueva para que Spring Data use {@code persist} en lugar de {@code merge}.
     * Se desactiva al cargar o persistir; una versión existente nunca se vuelve a guardar.
     */
    @Transient
    private boolean isNew = true;

    @Column(name = "version_number", nullable = false)
    private long versionNumber;

    @Column(name = "commission_pct", nullable = false, precision = 18, scale = 4)
    private BigDecimal commissionPct;

    @Column(name = "insurance_fee_per_passenger", nullable = false, precision = 18, scale = 4)
    private BigDecimal insuranceFeePerPassenger;

    @Column(name = "weekend_increase_pct", nullable = false, precision = 18, scale = 4)
    private BigDecimal weekendIncreasePct;

    @Column(name = "high_season_increase_pct", nullable = false, precision = 18, scale = 4)
    private BigDecimal highSeasonIncreasePct;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private String createdBy;

    protected FinancialParametersJpaEntity() {
    }

    static FinancialParametersJpaEntity from(FinancialParameters parameters) {
        FinancialParametersJpaEntity entity = new FinancialParametersJpaEntity();
        entity.id = parameters.id();
        entity.versionNumber = parameters.versionNumber();
        entity.commissionPct = parameters.commissionPct().value();
        entity.insuranceFeePerPassenger = parameters.insuranceFeePerPassenger().amount();
        entity.weekendIncreasePct = parameters.weekendIncreasePct().value();
        entity.highSeasonIncreasePct = parameters.highSeasonIncreasePct().value();
        entity.createdAt = parameters.createdAt();
        entity.createdBy = parameters.createdBy();
        return entity;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    long getVersionNumber() {
        return versionNumber;
    }

    BigDecimal getCommissionPct() {
        return commissionPct;
    }

    BigDecimal getInsuranceFeePerPassenger() {
        return insuranceFeePerPassenger;
    }

    BigDecimal getWeekendIncreasePct() {
        return weekendIncreasePct;
    }

    BigDecimal getHighSeasonIncreasePct() {
        return highSeasonIncreasePct;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    String getCreatedBy() {
        return createdBy;
    }
}
