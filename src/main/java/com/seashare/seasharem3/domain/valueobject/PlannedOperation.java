package com.seashare.seasharem3.domain.valueobject;

/**
 * Operación planificada por la política de estados de reserva (operación + desencadenante).
 * Fuente: UC07 plan T002 [CONV].
 * GAP: el tipo del desencadenante no está documentado; se conserva su valor textual.
 */
public record PlannedOperation(FinancialOperationType operationType, String trigger) {
}
