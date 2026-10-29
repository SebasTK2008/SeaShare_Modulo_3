package com.seashare.seasharem3.application.dto;

import java.util.UUID;

/**
 * Comando de cobro que UC05 publica en la cola interna {@code finance.charge-command.v1}
 * (outbox → worker).
 *
 * <p>Solo viaja el identificador de la intención y el correo del pagador: el token del medio de pago
 * se lee de {@code charge_intent} en el worker, de modo que el mensaje del outbox nunca transporta
 * credenciales de pago (CE-005). El correo es el dato plano que exige el contrato de cobro para
 * {@code payer.email}; puede venir ausente, y en ese caso el worker no envia el cobro.</p>
 *
 * <p>Fuente: UC05 plan T008, regla 4 y regla 7; contrato {@code pasarela-comando-cobro.md} §2.</p>
 */
public record ChargeCommand(UUID chargeIntentId, String payerEmail) {
}
