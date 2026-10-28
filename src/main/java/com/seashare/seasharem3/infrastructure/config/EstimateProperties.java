package com.seashare.seasharem3.infrastructure.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Propiedades de UC01: {@code seashare.estimates.*}.
 *
 * <p>{@code max-batch-size} lo exige el contrato de lote §2 y general-plan §7.4 / D-22; superarlo
 * devuelve {@code 400 BATCH_SIZE_EXCEEDED}.</p>
 */
@Validated
@ConfigurationProperties(prefix = "seashare.estimates")
public class EstimateProperties {

    /** Maximo de identificadores por lote (por defecto 50). */
    @Min(1)
    private int maxBatchSize = 50;

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public void setMaxBatchSize(int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
    }
}
