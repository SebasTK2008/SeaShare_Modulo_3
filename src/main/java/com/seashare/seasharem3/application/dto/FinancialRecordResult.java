package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.time.Instant;

/**
 * Detalle de un registro financiero inmutable.
 * Fuente: UC12 plan §Reglas 5 y contrato rest/UC12-consultar-registros-financieros.md §4.
 * GAP: recordType queda como String (COBRO/REEMBOLSO/DISPERSION/COMISION) porque no existe un enum compartido.
 */
public record FinancialRecordResult(String recordType, ReservationId reservationId, OwnerId ownerId, BoatId boatId,
                                    Money amount, Instant createdAt, String externalReference) {
}
