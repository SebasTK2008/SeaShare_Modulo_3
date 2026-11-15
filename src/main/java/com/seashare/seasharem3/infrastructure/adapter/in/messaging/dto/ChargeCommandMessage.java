package com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto;

import java.util.UUID;

/**
 * Cuerpo del comando de cobro publicado por UC05 en la cola interna
 * {@code finance.charge-command.v1} (outbox → worker).
 *
 * <p>Los componentes usan {@code camelCase} y el {@code JsonMapper} de Spring Boot aplica la
 * estrategia {@code SNAKE_CASE} del contrato, de modo que el cuerpo viaja como
 * {@code charge_intent_id} y {@code payer_email}. El token del medio de pago no viaja: el worker lo
 * lee de {@code charge_intent} (CE-005).</p>
 *
 * <p>Fuente: UC05 plan T008 y regla 4; contracts/README §3.1.</p>
 */
public record ChargeCommandMessage(UUID chargeIntentId, String payerEmail) {
}
