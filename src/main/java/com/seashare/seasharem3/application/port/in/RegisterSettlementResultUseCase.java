package com.seashare.seasharem3.application.port.in;

import com.seashare.seasharem3.application.dto.SettlementResultCommand;

/**
 * Puerto de entrada del registro del resultado de la liquidación (UC10), invocado por webhook/worker (T011).
 * Fuente: UC10 plan T011 y general-plan §3.5.
 * GAP: firma no publicada; se deriva el resultado de la pasarela como entrada.
 */
public interface RegisterSettlementResultUseCase {

    void register(SettlementResultCommand command);
}
