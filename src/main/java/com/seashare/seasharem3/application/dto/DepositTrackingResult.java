package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.DepositDispositionStatus;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

/**
 * Resultado interno de la apertura del seguimiento del depósito (compare-and-set, D-34).
 * Fuente: UC08 plan T007 y §Firmas referenciadas; [CONV] núcleo compartido: composición mínima congelada
 * (reserva, estado y si el llamador lo creó) para que UC07 pueda distinguir la apertura nueva del desenlace
 * ya existente.
 *
 * <p>{@code transitioned} es {@code true} solo si ESTA llamada aplicó el cambio hacia el desenlace pedido
 * (apertura o {@code HELD -> destino}); es {@code false} si el desenlace ya estaba aplicado por otra operación
 * o si hubo conflicto. UC09/UC10 lo usan para no crear una segunda intención sobre el mismo depósito (D-37).</p>
 */
public record DepositTrackingResult(ReservationId reservationId, DepositDispositionStatus status, boolean created,
        boolean transitioned) {
}
