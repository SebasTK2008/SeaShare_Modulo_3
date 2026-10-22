package com.seashare.seasharem3.application.port.in;

import com.seashare.seasharem3.application.dto.DepositTrackingResult;
import com.seashare.seasharem3.application.dto.OpenDepositTrackingCommand;

/**
 * Puerto publicado por UC08 para UC07 (plan §Firmas referenciadas): compare-and-set de deposit_disposition (D-34).
 * Firma publicada del método: open(OpenDepositTrackingCommand) → DepositTrackingResult (composición congelada).
 */
public interface OpenDepositTrackingUseCase {

    DepositTrackingResult open(OpenDepositTrackingCommand command);
}
