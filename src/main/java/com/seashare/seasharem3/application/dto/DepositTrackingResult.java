package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.DepositDispositionStatus;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

/**
 * Resultado interno de la apertura del seguimiento del depósito (compare-and-set, D-34).
 * Fuente: UC08 plan T007 y §Firmas referenciadas; [CONV] núcleo compartido: composición mínima congelada
 * (reserva, estado y si el llamador lo creó) para que UC07 pueda distinguir la apertura nueva del desenlace
 * ya existente.
 */
public record DepositTrackingResult(ReservationId reservationId, DepositDispositionStatus status, boolean created) {
}
