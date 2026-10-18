package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.time.LocalDate;

/**
 * Filtros, alcance declarado y paginación de la consulta de registros.
 * Fuente: UC12 plan T006 y contrato rest/UC12-consultar-registros-financieros.md §3.
 * GAP: composición exacta no publicada; scope y transactionType quedan como String (no hay enum RecordType en el núcleo).
 */
public record QueryFinancialRecordsCommand(String scope, int page, Integer size, String transactionType,
                                           OwnerId ownerId, BoatId boatId, ReservationId reservationId,
                                           LocalDate dateFrom, LocalDate dateTo) {
}
