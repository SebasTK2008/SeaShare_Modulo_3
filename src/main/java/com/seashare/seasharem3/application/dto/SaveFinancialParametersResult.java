package com.seashare.seasharem3.application.dto;

/**
 * Resultado del guardado de UC11: la versión vigente tras la operación.
 * El {@code PUT} solo necesita el número de versión; los datos derivados de solo lectura pertenecen
 * a la consulta ({@link FinancialParametersResult}).
 * Fuente: UC11 plan T017/T030; contrato rest/UC11-guardar-parametros-financieros.md §4.
 */
public record SaveFinancialParametersResult(long versionNumber) {
}
