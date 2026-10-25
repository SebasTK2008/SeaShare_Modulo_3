package com.seashare.seasharem3.domain.service;

import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.Money;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Calculo de estimacion de precio para UC01 (solo lectura, sin persistencia).
 * Formula: (tarifa base final * duracion) + (seguro * pasajeros).
 * La tarifa base ya incluye ajuste dinamico de UC02.
 * Fuente: UC01 spec RF-002, contrato rest/UC01-estimacion-*.md.
 */
public class PricingCalculator {

    public BigDecimal calculate(BaseRate baseRatePerDay, int durationDays, int passengers, Money insuranceFeePerPassenger) {
        Objects.requireNonNull(baseRatePerDay, "baseRatePerDay");
        Objects.requireNonNull(insuranceFeePerPassenger, "insuranceFeePerPassenger");
        BigDecimal rental = baseRatePerDay.amount().amount().multiply(BigDecimal.valueOf(durationDays));
        BigDecimal insurance = insuranceFeePerPassenger.amount().multiply(BigDecimal.valueOf(passengers));
        return rental.add(insurance);
    }
}
