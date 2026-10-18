package com.seashare.seasharem3.application.dto;

import java.util.List;

/** Página estable de registros (created_at DESC, record_id DESC). Fuente: UC12 plan T006 y contrato rest/UC12-consultar-registros-financieros.md §4. */
public record FinancialRecordsPageResult(List<FinancialRecordResult> content, int pageNumber, int pageSize,
                                         long totalElements, int totalPages) {
}
