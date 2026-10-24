package com.seashare.seasharem3.domain.valueobject;

import java.time.LocalDate;
import java.util.Objects;

/** Ventana de temporada alta del calendario de UC02. Fuente: UC02 plan T009 [CONV]. */
public record HighSeasonWindow(Type type, LocalDate startInclusive, LocalDate endInclusive) {

    public HighSeasonWindow {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(startInclusive, "startInclusive");
        Objects.requireNonNull(endInclusive, "endInclusive");
    }

    /** Tipos de ventana documentados en UC02 plan T009: type ∈ YEAR_END/MID_YEAR/HOLY_WEEK/RECESS_WEEK. */
    public enum Type {
        YEAR_END,
        MID_YEAR,
        HOLY_WEEK,
        RECESS_WEEK
    }
}
