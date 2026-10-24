package com.seashare.seasharem3.domain.service;

import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Aplica la tarifa dinámica de UC02 sobre la tarifa base provista por Flota.
 *
 * <p>La fecha regular no requiere parámetros. Fin de semana y temporada alta aplican el
 * porcentaje correspondiente; si ambas condiciones coinciden, se aplica el ajuste que produzca
 * la tarifa más alta. Fuente: UC02 SPEC RF-002, RF-006 y plan T011.</p>
 */
public class DynamicRatePolicy {

    private final HighSeasonCalendar highSeasonCalendar;

    public DynamicRatePolicy(HighSeasonCalendar highSeasonCalendar) {
        this.highSeasonCalendar = Objects.requireNonNull(highSeasonCalendar, "highSeasonCalendar");
    }

    /**
     * Indica si la fecha requiere leer los parámetros financieros vigentes.
     */
    public boolean requiresAdjustment(LocalDate date) {
        Objects.requireNonNull(date, "date");
        return isWeekend(date) || highSeasonCalendar.isHighSeason(date);
    }

    /**
     * Aplica la condición dinámica vigente. Si la fecha es regular no usa los parámetros; si
     * requiere ajuste, {@code parameters} y sus porcentajes deben estar presentes por invariante
     * de {@link FinancialParameters}. {@code ProvideBaseRateService} comprueba la ausencia de la
     * versión vigente antes de invocar este método.
     *
     * @throws NullPointerException si una fecha que requiere ajuste llega sin parámetros
     * @return tarifa final a escala 4, sin redondeo final a centavos
     */
    public BaseRate apply(BaseRate baseRate, LocalDate date, FinancialParameters parameters) {
        Objects.requireNonNull(baseRate, "baseRate");
        Objects.requireNonNull(date, "date");

        boolean weekend = isWeekend(date);
        boolean highSeason = highSeasonCalendar.isHighSeason(date);

        if (!weekend && !highSeason) {
            return baseRate;
        }
        FinancialParameters requiredParameters = Objects.requireNonNull(parameters, "parameters");

        if (weekend && highSeason) {
            Money weekendAmount = baseRate.amount().increaseBy(requiredParameters.weekendIncreasePct());
            Money highSeasonAmount = baseRate.amount().increaseBy(requiredParameters.highSeasonIncreasePct());
            return new BaseRate(weekendAmount.max(highSeasonAmount));
        }

        Percentage percentage = weekend
                ? requiredParameters.weekendIncreasePct()
                : requiredParameters.highSeasonIncreasePct();
        return new BaseRate(baseRate.amount().increaseBy(percentage));
    }

    private static boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}
