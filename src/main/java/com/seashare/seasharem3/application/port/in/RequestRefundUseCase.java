package com.seashare.seasharem3.application.port.in;

import com.seashare.seasharem3.application.dto.RefundRequestCommand;
import com.seashare.seasharem3.application.dto.RefundRequestResult;

/**
 * Firma publicada por UC09 (plan §Puertos expuestos): lo llaman UC07 y UC08.
 *
 * <p>Devuelve la intención registrada y su estado, o {@code FAILED} cuando la solicitud no pudo
 * registrarse (el detalle queda en {@code operational_failure}). UC07 usa ese resultado para
 * revertir la transacción del mensaje; UC08 puede ignorarlo.</p>
 */
public interface RequestRefundUseCase {

    RefundRequestResult request(RefundRequestCommand command);
}
