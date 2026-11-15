package com.seashare.seasharem3.domain.service;

import com.seashare.seasharem3.domain.exception.DomainException;

import java.time.LocalDate;

/**
 * Calcula el Domingo de Resurrección con el algoritmo de Meeus/Jones/Butcher.
 * Fuente: UC02 SPEC RF-006/RNF-004 y plan T008.
 */
public final class EasterCalculator {

    private static final int GREGORIAN_MIN_YEAR = 1583;

    private EasterCalculator() {
    }

    /**
     * Devuelve el Domingo de Resurrección del año indicado en el calendario gregoriano.
     *
     * @param year año a calcular; debe ser mayor o igual a 1583
     * @return fecha del Domingo de Resurrección
     */
    public static LocalDate easterSunday(int year) {
        if (year < GREGORIAN_MIN_YEAR) {
            throw new DomainException("Easter calculation requires a Gregorian year >= " + GREGORIAN_MIN_YEAR
                    + ": " + year);
        }

        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;

        return LocalDate.of(year, month, day);
    }
}
