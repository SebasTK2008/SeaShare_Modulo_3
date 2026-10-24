package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.SettlementStatus;

import java.util.UUID;

/**
 * Resultado de la solicitud de liquidación: intención registrada y su estado.
 * Fuente: UC10 plan §Puerto interno (`SettlementRequestResult request(SettlementCommand command)`); [CONV] núcleo
 * compartido: composición mínima congelada (id + estado) para que UC07/UC08 puedan ignorarla sin bloquearse.
 */
public record SettlementRequestResult(UUID settlementIntentId, SettlementStatus status) {
}
