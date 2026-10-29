package com.seashare.seasharem3.infrastructure.adapter.in.scheduler;

import com.seashare.seasharem3.application.port.in.ReconcileChargeIntentsUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job programado de conciliación de los cobros sin resultado definitivo (UC05); se comparte con
 * UC09 y UC10, que registran sus propios manejadores. ShedLock garantiza una sola instancia activa.
 * Fuente: UC05 plan T023; general-plan §7.2.
 */
@Component
public class GatewayReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(GatewayReconciliationJob.class);

    private final ReconcileChargeIntentsUseCase reconcileChargeIntents;

    public GatewayReconciliationJob(ReconcileChargeIntentsUseCase reconcileChargeIntents) {
        this.reconcileChargeIntents = reconcileChargeIntents;
    }

    @Scheduled(fixedDelayString = "${seashare.gateway.reconciliation-interval:5m}")
    @SchedulerLock(name = "gatewayReconciliationJob", lockAtMostFor = "PT4M", lockAtLeastFor = "PT1S")
    public void reconcile() {
        reconcileChargeIntents.reconcile();
        log.info("Charge reconciliation cycle finished");
    }
}
