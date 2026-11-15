package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.RefundIntentStatus;

import java.util.UUID;

/**
 * Resultado de la solicitud de reembolso: intención registrada y su estado.
 * Fuente: UC09 plan §Puerto interno; [CONV] núcleo compartido: composición mínima congelada
 * (id + estado) para que UC07/UC08 puedan ignorarla sin bloquearse.
 *
 * <p>{@code refundIntentId} es {@code null} y el estado {@code FAILED} cuando la solicitud no pudo
 * registrarse; el detalle queda en {@code operational_failure}.</p>
 */
public record RefundRequestResult(UUID refundIntentId, RefundIntentStatus status) {
}
