package com.seashare.seasharem3.infrastructure.adapter.in.scheduler;

import com.seashare.seasharem3.application.port.in.ReconcileChargeIntentsUseCase;
import com.seashare.seasharem3.application.port.in.ReconcileRefundIntentsUseCase;
import com.seashare.seasharem3.application.port.in.ReconcileSettlementIntentsUseCase;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** El ciclo concilia cobros, reembolsos y liquidaciones; un fallo en uno no impide los demas. */
class GatewayReconciliationJobTest {

    private final ReconcileChargeIntentsUseCase charges = mock(ReconcileChargeIntentsUseCase.class);
    private final ReconcileRefundIntentsUseCase refunds = mock(ReconcileRefundIntentsUseCase.class);
    private final ReconcileSettlementIntentsUseCase settlements = mock(ReconcileSettlementIntentsUseCase.class);
    private final GatewayReconciliationJob job = new GatewayReconciliationJob(charges, refunds, settlements);

    @Test
    void reconcilesChargesRefundsAndSettlementsInOrder() {
        job.reconcile();

        InOrder order = inOrder(charges, refunds, settlements);
        order.verify(charges).reconcile();
        order.verify(refunds).reconcile();
        order.verify(settlements).reconcile();
    }

    @Test
    void aFailingChargeReconciliationDoesNotStopTheOthers() {
        doThrow(new IllegalStateException("gateway down")).when(charges).reconcile();

        assertThatCode(job::reconcile).doesNotThrowAnyException();

        verify(refunds).reconcile();
        verify(settlements).reconcile();
    }

    @Test
    void aFailingRefundReconciliationStillAllowsTheSettlementOne() {
        doThrow(new IllegalStateException("gateway down")).when(refunds).reconcile();

        assertThatCode(job::reconcile).doesNotThrowAnyException();

        verify(charges).reconcile();
        verify(settlements).reconcile();
    }
}
