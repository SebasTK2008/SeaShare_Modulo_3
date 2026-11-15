package com.seashare.seasharem3.application.port.in;

import com.seashare.seasharem3.application.dto.ProcessChargeCommand;

/** Firma publicada por UC05 (plan §Puertos expuestos): invocado por UC07 con el estado PENDING. */
public interface ProcessChargeUseCase {

    void process(ProcessChargeCommand command);
}
