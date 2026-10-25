package com.seashare.seasharem3.domain.service;

import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.valueobject.Money;

import java.util.Objects;

/**
 * Calculo de estimacion de precio para UC01 (solo lectura, sin persistencia).
 * Formula: (tarifa base final * duracion) + (seguro * pasajeros).
 * La tarifa base ya incluye ajuste dinamico de UC02.
 * Fuente: UC01 spec RF-002, contrato rest/UC01-estimacion-*.md; plan T005.
 */
public class PricingCalculator {

    /**
     * Estima el total de una reserva.
     *
     * @param finalBaseRatePerDay tarifa base final (ya ajustada por UC02), por dia
     * @param durationDays        dias inclusivos de la reserva (>= 1)
     * @param insuranceFeePerPassenger seguro por pasajero
     * @param passengers          numero de pasajeros (>= 1)
     */
    public Money estimate(Money finalBaseRatePerDay, int durationDays,
                          Money insuranceFeePerPassenger, int passengers) {
        Objects.requireNonNull(finalBaseRatePerDay, "finalBaseRatePerDay");
        Objects.requireNonNull(insuranceFeePerPassenger, "insuranceFeePerPassenger");
        if (durationDays < 1) {
            throw new DomainException("durationDays must be at least 1: " + durationDays);
        }
        if (passengers < 1) {
            throw new DomainException("passengers must be at least 1: " + passengers);
        }
        Money rental = finalBaseRatePerDay.multiply(durationDays);
        Money insurance = insuranceFeePerPassenger.multiply(passengers);
        return rental.add(insurance);
    }
}
