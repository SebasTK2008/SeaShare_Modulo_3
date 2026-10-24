package com.seashare.seasharem3.application.port.in;

import com.seashare.seasharem3.application.dto.RefundRequestCommand;

/** Firma publicada por UC09 (plan §Puertos expuestos): lo llaman UC07 y UC08. */
public interface RequestRefundUseCase {

    void request(RefundRequestCommand command);
}
