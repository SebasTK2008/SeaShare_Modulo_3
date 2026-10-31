package com.seashare.seasharem3.domain.service;

import com.seashare.seasharem3.domain.valueobject.FinancialOperationType;
import com.seashare.seasharem3.domain.valueobject.PlannedOperation;
import com.seashare.seasharem3.domain.valueobject.RefundTrigger;
import com.seashare.seasharem3.domain.valueobject.ReservationStatus;
import com.seashare.seasharem3.domain.valueobject.SettlementTrigger;

import java.util.List;
import java.util.Objects;

/**
 * Politica pura estado de la reserva -> operaciones financieras (SPEC 07 RF-001..RF-008; UC07 plan
 * §Reglas 2-5).
 *
 * <p>Solo devuelve operaciones monetarias: el seguimiento del deposito que abre {@code COMPLETED}
 * no es una operacion sobre el deposito y lo aplica el servicio tras la liquidacion (RF-007,
 * D-UC07-06). El estado sin accion financiera devuelve una lista vacia; nunca {@code null}.</p>
 */
public final class ReservationStatusPolicy {

    public List<PlannedOperation> planFor(ReservationStatus status) {
        Objects.requireNonNull(status, "status");

        return switch (status) {
            case AVAILABLE, INITIATED, RESERVED, IN_NAVIGATION -> List.of();
            case PENDING -> List.of(new PlannedOperation(FinancialOperationType.CHARGE, null));
            case CANCELLED_FLEXIBLE -> List.of(refund(RefundTrigger.CANCELLED_FLEXIBLE));
            case CANCELLED_MODERATE -> List.of(refund(RefundTrigger.CANCELLED_MODERATE),
                    settlement(SettlementTrigger.CANCELLED_MODERATE));
            case CANCELLED_STRICT -> List.of(settlement(SettlementTrigger.CANCELLED_STRICT),
                    refund(RefundTrigger.CANCELLED_STRICT));
            case CANCELLED_BY_HOST -> List.of(refund(RefundTrigger.CANCELLED_BY_HOST));
            case COMPLETED -> List.of(settlement(SettlementTrigger.COMPLETED));
        };
    }

    private static PlannedOperation refund(RefundTrigger trigger) {
        return new PlannedOperation(FinancialOperationType.REFUND, trigger.name());
    }

    private static PlannedOperation settlement(SettlementTrigger trigger) {
        return new PlannedOperation(FinancialOperationType.SETTLEMENT, trigger.name());
    }
}
