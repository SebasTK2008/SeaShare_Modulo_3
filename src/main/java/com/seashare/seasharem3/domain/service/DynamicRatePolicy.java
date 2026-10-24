package com.seashare.seasharem3.domain.service;

import com.seashare.seasharem3.domain.exception.BaseRateException;
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
     * requiere ajuste y no hay parámetros, falla con {@code DYNAMIC_RATE_NOT_CONFIGURED}.
     *
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
        if (parameters == null) {
            throw notConfigured();
        }

        if (weekend && highSeason) {
            Money weekendAmount = baseRate.amount().increaseBy(
                    requiredPercentage(parameters.weekendIncreasePct()));
            Money highSeasonAmount = baseRate.amount().increaseBy(
                    requiredPercentage(parameters.highSeasonIncreasePct()));
            return new BaseRate(weekendAmount.max(highSeasonAmount));
        }

        Percentage percentage = weekend
                ? parameters.weekendIncreasePct()
                : parameters.highSeasonIncreasePct();
        return new BaseRate(baseRate.amount().increaseBy(requiredPercentage(percentage)));
    }

    private static boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    private static Percentage requiredPercentage(Percentage percentage) {
        if (percentage == null) {
            throw notConfigured();
        }
        return percentage;
    }

    private static BaseRateException notConfigured() {
        return new BaseRateException(BaseRateException.BaseRateFailureReason.DYNAMIC_RATE_NOT_CONFIGURED,
                "A dynamic rate percentage is required for the evaluated date but is not configured");
    }
}
