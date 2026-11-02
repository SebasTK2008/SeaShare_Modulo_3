package com.seashare.seasharem3.domain.service;

import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import com.seashare.seasharem3.domain.valueobject.ReservationValue;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Cálculo puro del desglose financiero de una reserva ya registrada: alquiler, seguro náutico,
 * depósito de garantía y valor total.
 *
 * <p>Solo usa la información registrada por UC03 y la versión de parámetros resuelta: nunca consulta
 * Flota ni recalcula la tarifa base ni la dinámica [SPEC CE-002]. Fuente: UC04 plan T008;
 * SPEC RF-003–RF-006 y RNF-002; general-plan D-29 (depósito) y contracts/README §3.1 (precisión
 * interna de 4 decimales y salida monetaria a 2 con HALF_UP).</p>
 */
public class ReservationValueCalculator {

    /**
     * Depósito de garantía = 10 % de la tarifa diaria vigente registrada, sin multiplicarlo por la
     * duración, los pasajeros ni el daño [SPEC RF-005, HU1; general-plan D-29].
     */
    private static final Percentage GUARANTEE_DEPOSIT_PCT = new Percentage(new BigDecimal("10"));

    /**
     * Devuelve el desglose completo conservando la escala interna de cuatro decimales. El redondeo
     * de presentación se aplica únicamente al serializar la respuesta.
     */
    public ReservationValue calculate(ReservationInformation information, FinancialParameters parameters) {
        Objects.requireNonNull(information, "information");
        Objects.requireNonNull(parameters, "parameters");

        Money dailyRate = information.baseRate().amount();
        Money rentalAmount = dailyRate.multiply(inclusiveDays(information));
        Money insuranceAmount = parameters.insuranceFeePerPassenger().multiply(information.passengers());
        Money guaranteeDeposit = dailyRate.percentageOf(GUARANTEE_DEPOSIT_PCT);
        Money totalAmount = rentalAmount.add(insuranceAmount).add(guaranteeDeposit);

        return new ReservationValue(rentalAmount, insuranceAmount, guaranteeDeposit, totalAmount);
    }

    /** Días inclusivos: inicio = fin ⇒ 1 día; del día 10 al 12 ⇒ 3 días [SPEC RF-003]. */
    private static long inclusiveDays(ReservationInformation information) {
        return ChronoUnit.DAYS.between(information.startDate(), information.endDate()) + 1;
    }

}
