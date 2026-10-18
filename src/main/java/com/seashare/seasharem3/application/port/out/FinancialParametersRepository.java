package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.domain.model.FinancialParameters;

import java.util.Optional;

/** Firma publicada en UC11 plan §Puertos expuestos; consumido por UC01, UC02 y UC04. */
public interface FinancialParametersRepository {

    Optional<FinancialParameters> findCurrent();

    FinancialParameters saveNewVersion(FinancialParameters params);
}
