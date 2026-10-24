package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.Periodicity;
import com.seashare.seasharem3.domain.valueobject.ReportPeriod;
import com.seashare.seasharem3.domain.valueobject.ReportScope;

/**
 * Parámetros del informe: periodicidad, alcance, propietario declarado y período (opcional).
 * Fuente: UC13 plan T006 y contrato rest/UC13-consultar-informe-financiero.md §3.
 * GAP: composición exacta no publicada; ReportPeriod es un VO del núcleo con composición pendiente.
 */
public record GetFinancialReportCommand(Periodicity periodicity, ReportScope scope, OwnerId ownerId,
                                        ReportPeriod period) {
}
