package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.Money;

/**
 * Resultado tecnico del reembolso en la pasarela (= ResultadoReembolsoPasarela [SPEC]).
 * Fuente: UC09 plan T011 y contrato external/pasarela-comando-reembolso.md.
 * {@code statusDetail} y {@code transactionCost} son nullable porque el contrato solo garantiza
 * {@code status}, {@code id} y {@code amount}; cuando la pasarela no los informa quedan en null.
 */
public record RefundGatewayResult(String status, String externalId, Money amount, String statusDetail,
                                  Money transactionCost) {
}
