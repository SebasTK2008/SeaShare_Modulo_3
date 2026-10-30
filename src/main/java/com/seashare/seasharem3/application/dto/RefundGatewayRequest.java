package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.RefundOperationType;

import java.util.UUID;

/**
 * Solicitud de reembolso a la pasarela (= SolicitudReembolsoPasarela [SPEC]).
 * Fuente: UC09 plan T011 y contrato external/pasarela-comando-reembolso.md.
 * {@code originalChargeReference} es el pago original y {@code operationKey} viaja como
 * {@code X-Idempotency-Key}. El cobro es directo con captura automatica, por lo que la unica
 * operacion posible es REFUND (general-plan D-32).
 */
public record RefundGatewayRequest(RefundOperationType operationType, String originalChargeReference,
                                   UUID reservationId, Money amount, String operationKey) {
}
