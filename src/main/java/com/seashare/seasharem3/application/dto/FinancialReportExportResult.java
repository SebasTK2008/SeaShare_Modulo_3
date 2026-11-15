package com.seashare.seasharem3.application.dto;

import java.time.Instant;

/**
 * Datos para la exportación CSV: reutiliza exactamente el informe y añade los metadatos de la cabecera.
 * Fuente: UC13 plan §Reglas 9 y 11; contrato rest/UC13-exportar-informe-financiero.md §4.
 */
public record FinancialReportExportResult(FinancialReportResult report, Instant generatedAt) {
}
