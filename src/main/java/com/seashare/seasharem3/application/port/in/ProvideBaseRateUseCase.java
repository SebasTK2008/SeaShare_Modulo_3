package com.seashare.seasharem3.application.port.in;

import com.seashare.seasharem3.application.dto.BaseRateResult;
import com.seashare.seasharem3.application.dto.ProvideBaseRateCommand;

import java.util.List;

/**
 * Puerto publicado por UC02 (plan §Firmas referenciadas por otros planes): devuelve la tarifa final
 * solo de las embarcaciones con tarifa disponible; falla la invocación completa con BaseRateException.
 */
public interface ProvideBaseRateUseCase {

    List<BaseRateResult> provideBaseRates(ProvideBaseRateCommand command);
}
