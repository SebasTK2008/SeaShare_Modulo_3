package com.seashare.seasharem3.domain.valueobject;

import java.util.Objects;

/**
 * Operación planificada por la política de estados de reserva (operación + desencadenante).
 * Fuente: UC07 plan T002 [CONV].
 *
 * <p><b>Invariante del desencadenante:</b> en {@code REFUND} es el nombre literal de un
 * {@link RefundTrigger} y en {@code SETTLEMENT} el de un {@link SettlementTrigger}, en mayúsculas;
 * en {@code CHARGE} es {@code null} porque el cobro no tiene desencadenante propio. El servicio que
 * delega lo convierte al enum correspondiente y falla rápido si no coincide (D-UC07-11).</p>
 */
public record PlannedOperation(FinancialOperationType operationType, String trigger) {

    public PlannedOperation {
        Objects.requireNonNull(operationType, "operationType");
    }
}
