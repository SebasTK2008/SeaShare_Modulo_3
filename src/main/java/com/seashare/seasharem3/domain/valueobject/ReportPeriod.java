package com.seashare.seasharem3.domain.valueobject;

/**
 * Período de informe en formato canónico según periodicidad (`YYYY-MM-H1`, `YYYY-MM`, `YYYY-Q1`…).
 * Fuente: contracts/README.md §3.1 y contrato rest/UC13-consultar-informe-financiero.md §2.
 * GAP: la composición exacta de ReportPeriod no está documentada; se usa periodicidad + valor canónico.
 */
public record ReportPeriod(Periodicity periodicity, String value) {
}
