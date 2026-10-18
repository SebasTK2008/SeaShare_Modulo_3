package com.seashare.seasharem3.infrastructure.adapter.in.scheduler;

import com.seashare.seasharem3.application.port.in.RelayOutboxUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job programado del relay del outbox; ShedLock garantiza una sola instancia activa.
 * Fuente: UC11 plan T046 (OutboxRelayJob del núcleo compartido); general-plan D-08.
 * GAP: los jobs de conciliación por UC (GatewayReconciliationJob) y de disputas son de UC05/UC08.
 */
@Component
public class OutboxRelayJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayJob.class);

    private final RelayOutboxUseCase relayOutboxUseCase;

    public OutboxRelayJob(RelayOutboxUseCase relayOutboxUseCase) {
        this.relayOutboxUseCase = relayOutboxUseCase;
    }

    @Scheduled(fixedDelayString = "${seashare.outbox.relay-interval:5s}")
    @SchedulerLock(name = "outboxRelayJob", lockAtMostFor = "PT4M", lockAtLeastFor = "PT1S")
    public void relayOutbox() {
        int published = relayOutboxUseCase.relay();
        if (published > 0) {
            log.info("Outbox relay: {} mensaje(s) publicado(s)", published);
        }
    }
}
