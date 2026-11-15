package com.seashare.seasharem3.infrastructure.config;

import com.seashare.seasharem3.application.port.in.RequestRefundUseCase;
import com.seashare.seasharem3.application.port.in.RequestSettlementUseCase;
import com.seashare.seasharem3.application.port.out.DepositDispositionRepository;
import com.seashare.seasharem3.application.port.out.DisputeEventLogRepository;
import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.application.port.out.ReservationLockPort;
import com.seashare.seasharem3.application.service.DisputeInformationService;
import com.seashare.seasharem3.application.service.OpenDepositTrackingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Ensambla UC08: desenlace del deposito y procesamiento de notificaciones de disputa. */
@Configuration(proxyBeanMethods = false)
public class DepositConfiguration {

    @Bean
    public OpenDepositTrackingService openDepositTrackingService(DepositDispositionRepository repository,
            ReservationLockPort reservationLock, FailureRecorderPort failureRecorder, Clock instantClock) {
        return new OpenDepositTrackingService(repository, reservationLock, failureRecorder, instantClock);
    }

    @Bean
    public DisputeInformationService disputeInformationService(DisputeEventLogRepository eventLogRepository,
            DepositDispositionRepository depositDispositionRepository, ReservationLockPort reservationLock,
            FailureRecorderPort failureRecorder, RequestRefundUseCase requestRefund,
            RequestSettlementUseCase requestSettlement, Clock instantClock) {
        return new DisputeInformationService(eventLogRepository, depositDispositionRepository, reservationLock,
                failureRecorder, requestRefund, requestSettlement, instantClock);
    }
}
