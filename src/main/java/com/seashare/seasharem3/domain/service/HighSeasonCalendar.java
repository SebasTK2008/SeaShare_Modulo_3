package com.seashare.seasharem3.domain.service;

import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.valueobject.HighSeasonWindow;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Calendario exacto de temporada alta definido por UC02.
 *
 * <p>Ventanas por año: fin de año (15-nov a 15-ene del año siguiente), mitad de año
 * (01-jun a 30-jul), Semana Santa (Jueves y Viernes Santo) y semana de receso (05-oct a 12-oct).
 * Fuente: UC02 SPEC RF-006 y plan T009.</p>
 */
public class HighSeasonCalendar {

    private static final int GREGORIAN_MIN_YEAR = 1583;

    /**
     * Indica si la fecha cae dentro de alguna ventana de temporada alta. Se revisan las ventanas
     * del año de la fecha y las del año anterior para cubrir el tramo de enero que pertenece a la
     * ventana de fin de año abierta el 15 de noviembre anterior.
     */
    public boolean isHighSeason(LocalDate date) {
        Objects.requireNonNull(date, "date");

        if (contains(windowsForYear(date.getYear()), date)) {
            return true;
        }
        return date.getYear() > GREGORIAN_MIN_YEAR
                && contains(windowsForYear(date.getYear() - 1), date);
    }

    /**
     * Devuelve las cuatro ventanas de temporada alta del año indicado. La ventana de fin de año
     * termina el 15 de enero del año siguiente.
     */
    public List<HighSeasonWindow> windowsForYear(int year) {
        if (year < GREGORIAN_MIN_YEAR) {
            throw new DomainException("High season calendar requires a Gregorian year >= "
                    + GREGORIAN_MIN_YEAR + ": " + year);
        }

        LocalDate easterSunday = EasterCalculator.easterSunday(year);

        return List.of(
                new HighSeasonWindow(HighSeasonWindow.Type.YEAR_END,
                        LocalDate.of(year, 11, 15),
                        LocalDate.of(year + 1, 1, 15)),
                new HighSeasonWindow(HighSeasonWindow.Type.MID_YEAR,
                        LocalDate.of(year, 6, 1),
                        LocalDate.of(year, 7, 30)),
                new HighSeasonWindow(HighSeasonWindow.Type.HOLY_WEEK,
                        easterSunday.minusDays(3),
                        easterSunday.minusDays(2)),
                new HighSeasonWindow(HighSeasonWindow.Type.RECESS_WEEK,
                        LocalDate.of(year, 10, 5),
                        LocalDate.of(year, 10, 12)));
    }

    private static boolean contains(List<HighSeasonWindow> windows, LocalDate date) {
        return windows.stream().anyMatch(window ->
                !date.isBefore(window.startInclusive()) && !date.isAfter(window.endInclusive()));
    }
}
