package com.seashare.seasharem3.application.port.in;

import com.seashare.seasharem3.application.dto.SettlementCommand;
import com.seashare.seasharem3.application.dto.SettlementRequestResult;

/** Firma publicada por UC10 (plan §Puerto interno): SettlementRequestResult request(SettlementCommand command). */
public interface RequestSettlementUseCase {

    SettlementRequestResult request(SettlementCommand command);
}
