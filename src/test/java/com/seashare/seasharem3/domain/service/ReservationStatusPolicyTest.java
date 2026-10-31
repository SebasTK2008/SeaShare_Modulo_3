package com.seashare.seasharem3.domain.service;

import com.seashare.seasharem3.domain.valueobject.FinancialOperationType;
import com.seashare.seasharem3.domain.valueobject.PlannedOperation;
import com.seashare.seasharem3.domain.valueobject.ReservationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Pruebas de la política estado -> operaciones (UC07 plan T003; SPEC 07 RF-001..RF-008 y CE-001).
 */
class ReservationStatusPolicyTest {

    private final ReservationStatusPolicy policy = new ReservationStatusPolicy();

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = {"AVAILABLE", "INITIATED", "RESERVED", "IN_NAVIGATION"})
    void operatingStatesHaveNoFinancialAction(ReservationStatus status) {
        assertThat(policy.planFor(status)).isEmpty();
    }

    @Test
    void pendingOnlyDispatchesTheCharge() {
        assertThat(policy.planFor(ReservationStatus.PENDING))
                .containsExactly(new PlannedOperation(FinancialOperationType.CHARGE, null));
    }

    @Test
    void flexibleCancellationOnlyRefunds() {
        assertThat(policy.planFor(ReservationStatus.CANCELLED_FLEXIBLE))
                .containsExactly(new PlannedOperation(FinancialOperationType.REFUND, "CANCELLED_FLEXIBLE"));
    }

    @Test
    void moderateCancellationRefundsAndCompensatesWithTwoOperations() {
        assertThat(policy.planFor(ReservationStatus.CANCELLED_MODERATE))
                .containsExactly(new PlannedOperation(FinancialOperationType.REFUND, "CANCELLED_MODERATE"),
                        new PlannedOperation(FinancialOperationType.SETTLEMENT, "CANCELLED_MODERATE"));
    }

    @Test
    void strictCancellationCompensatesAndRefundsTheDeposit() {
        assertThat(policy.planFor(ReservationStatus.CANCELLED_STRICT))
                .containsExactly(new PlannedOperation(FinancialOperationType.SETTLEMENT, "CANCELLED_STRICT"),
                        new PlannedOperation(FinancialOperationType.REFUND, "CANCELLED_STRICT"));
    }

    @Test
    void hostCancellationOnlyRefunds() {
        assertThat(policy.planFor(ReservationStatus.CANCELLED_BY_HOST))
                .containsExactly(new PlannedOperation(FinancialOperationType.REFUND, "CANCELLED_BY_HOST"));
    }

    @Test
    void completedOnlyRequestsTheStandardSettlement() {
        assertThat(policy.planFor(ReservationStatus.COMPLETED))
                .containsExactly(new PlannedOperation(FinancialOperationType.SETTLEMENT, "COMPLETED"));
    }

    @Test
    void noStatePlansAChargeWithADepositBackedOperation() {
        for (ReservationStatus status : ReservationStatus.values()) {
            assertThat(policy.planFor(status))
                    .as("plan of %s", status)
                    .allSatisfy(operation -> {
                        assertThat(operation.operationType()).isIn(FinancialOperationType.CHARGE,
                                FinancialOperationType.REFUND, FinancialOperationType.SETTLEMENT);
                        if (operation.operationType() == FinancialOperationType.CHARGE) {
                            assertThat(operation.trigger()).isNull();
                        } else {
                            assertThat(operation.trigger()).isNotBlank();
                        }
                    });
        }
    }

    @Test
    void rejectsANullStatus() {
        assertThatNullPointerException().isThrownBy(() -> policy.planFor(null)).withMessage("status");
    }

    @Test
    void plansAreImmutable() {
        List<PlannedOperation> plan = policy.planFor(ReservationStatus.CANCELLED_MODERATE);
        assertThat(plan).isUnmodifiable();
    }
}
