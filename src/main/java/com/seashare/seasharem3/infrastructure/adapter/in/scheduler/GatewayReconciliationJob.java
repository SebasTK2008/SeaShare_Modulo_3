package com.seashare.seasharem3.infrastructure.adapter.in.scheduler;

import com.seashare.seasharem3.application.port.in.ReconcileChargeIntentsUseCase;
import com.seashare.seasharem3.application.port.in.ReconcileRefundIntentsUseCase;
import com.seashare.seasharem3.application.port.in.ReconcileSettlementIntentsUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job programado de conciliacion de cobros, reembolsos y liquidaciones sin resultado definitivo.
 * ShedLock garantiza una sola instancia activa. Fuente: UC05/UC09/UC10 y general-plan §7.2.
 */
@Component
public class GatewayReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(GatewayReconciliationJob.class);

    private final ReconcileChargeIntentsUseCase reconcileChargeIntents;
    private final ReconcileRefundIntentsUseCase reconcileRefundIntents;
    private final ReconcileSettlementIntentsUseCase reconcileSettlementIntents;

    public GatewayReconciliationJob(ReconcileChargeIntentsUseCase reconcileChargeIntents,
            ReconcileRefundIntentsUseCase reconcileRefundIntents,
            ReconcileSettlementIntentsUseCase reconcileSettlementIntents) {
        this.reconcileChargeIntents = reconcileChargeIntents;
        this.reconcileRefundIntents = reconcileRefundIntents;
        this.reconcileSettlementIntents = reconcileSettlementIntents;
    }

    @Scheduled(fixedDelayString = "${seashare.gateway.reconciliation-interval:5m}")
    @SchedulerLock(name = "gatewayReconciliationJob", lockAtMostFor = "PT4M", lockAtLeastFor = "PT1S")
    public void reconcile() {
        int failed = runCycle();
        if (failed == 0) {
            log.info("Charge, refund and settlement reconciliation cycle finished");
        } else {
            log.warn("Reconciliation cycle finished with {} of 3 reconciliations failed", failed);
        }
    }

    /**
     * Ejecuta las tres conciliaciones y devuelve cuantas fallaron. Cada una es independiente: si la pasarela
     * falla al conciliar cobros, los reembolsos y la liquidacion interna (que no usa la pasarela) igual deben
     * conciliarse en este ciclo.
     */
    int runCycle() {
        int failed = 0;
        failed += runIsolated("charge", reconcileChargeIntents::reconcile);
        failed += runIsolated("refund", reconcileRefundIntents::reconcile);
        failed += runIsolated("settlement", reconcileSettlementIntents::reconcile);
        return failed;
    }

    private static int runIsolated(String name, Runnable reconciliation) {
        try {
            reconciliation.run();
            return 0;
        } catch (RuntimeException exception) {
            log.error("The {} reconciliation failed; the next one still runs", name, exception);
            return 1;
        }
    }
}
