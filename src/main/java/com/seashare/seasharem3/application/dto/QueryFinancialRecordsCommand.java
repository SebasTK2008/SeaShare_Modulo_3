package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.FinancialRecordType;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.ReportScope;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.time.LocalDate;

/**
 * Filtros, alcance declarado y paginación de la consulta de registros.
 * Fuente: UC12 plan T006 y contrato rest/UC12-consultar-registros-financieros.md §3.
 * {@code scope} y {@code transactionType} usan los enums compartidos {@link ReportScope} y
 * {@link FinancialRecordType} (general-plan D-18).
 */
public record QueryFinancialRecordsCommand(ReportScope scope, int page, Integer size, FinancialRecordType transactionType,
                                           OwnerId ownerId, BoatId boatId, ReservationId reservationId,
                                           LocalDate dateFrom, LocalDate dateTo) {
}
