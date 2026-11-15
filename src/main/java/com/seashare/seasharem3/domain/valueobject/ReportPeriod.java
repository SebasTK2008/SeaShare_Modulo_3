package com.seashare.seasharem3.domain.valueobject;

/**
 * Período de informe en formato canónico según periodicidad (`YYYY-MM-H1`, `YYYY-MM`, `YYYY-Q1`…).
 * Fuente: contracts/README.md §3.1 y contrato rest/UC13-consultar-informe-financiero.md §2.
 */
public record ReportPeriod(Periodicity periodicity, String value) {
}
