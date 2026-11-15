package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.valueobject.BoatId;

import java.time.LocalDate;
import java.util.List;

/**
 * Firma publicada por UC02 (plan §Firmas referenciadas): lista obligatoria (vacía ⇒ sin consultar a
 * Flota) y fecha evaluada obligatoria.
 *
 * <p>{@code parameters} es la instantánea de la versión vigente que el llamador ya leyó. UC01 la pasa
 * para que el seguro y los porcentajes de una misma estimación provengan de una única versión
 * (M6). Si es {@code null}, UC02 mantiene la lectura perezosa del repositorio para llamadores que no
 * dispongan de la instantánea.</p>
 */
public record ProvideBaseRateCommand(List<BoatId> boatIds, LocalDate evaluatedDate,
        FinancialParameters parameters) {

    /** Constructor compatible con los llamadores que no tienen una instantánea previa. */
    public ProvideBaseRateCommand(List<BoatId> boatIds, LocalDate evaluatedDate) {
        this(boatIds, evaluatedDate, null);
    }
}
