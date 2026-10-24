package com.seashare.seasharem3.application.port.in;

import com.seashare.seasharem3.application.dto.PaymentConfirmationQuery;
import com.seashare.seasharem3.application.dto.PaymentConfirmationResult;

/**
 * Puerto de entrada de la confirmación de pago (UC06, solo lectura).
 * Fuente: UC06 plan T003/T005 y general-plan §3.5.
 * GAP: firma no publicada por el plan; derivada del nombre del caso de uso.
 */
public interface GetPaymentConfirmationUseCase {

    PaymentConfirmationResult get(PaymentConfirmationQuery query);
}
