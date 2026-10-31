package com.seashare.seasharem3.infrastructure.config;

import com.seashare.seasharem3.application.port.in.OpenDepositTrackingUseCase;
import com.seashare.seasharem3.application.port.in.ProcessChargeUseCase;
import com.seashare.seasharem3.application.port.in.RequestRefundUseCase;
import com.seashare.seasharem3.application.port.in.RequestSettlementUseCase;
import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.application.port.out.ReservationInformationRepository;
import com.seashare.seasharem3.application.port.out.ReservationLockPort;
import com.seashare.seasharem3.application.port.out.ReservationStatusLogRepository;
import com.seashare.seasharem3.application.service.HandleReservationStatusService;
import com.seashare.seasharem3.domain.service.ReservationStatusPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Ensambla UC07: política de estados, identidad única de operación y delegación en UC05/UC08/UC09/UC10. */
@Configuration(proxyBeanMethods = false)
public class ReservationStatusConfiguration {

    @Bean
    public ReservationStatusPolicy reservationStatusPolicy() {
        return new ReservationStatusPolicy();
    }

    @Bean
    public HandleReservationStatusService handleReservationStatusService(
            ReservationInformationRepository reservationInformation, ReservationStatusLogRepository statusLog,
            ReservationLockPort reservationLock, FailureRecorderPort failureRecorder,
            ProcessChargeUseCase processCharge, RequestRefundUseCase requestRefund,
            RequestSettlementUseCase requestSettlement, OpenDepositTrackingUseCase openDepositTracking,
            ReservationStatusPolicy reservationStatusPolicy, Clock clock) {
        return new HandleReservationStatusService(reservationInformation, statusLog, reservationLock,
                failureRecorder, processCharge, requestRefund, requestSettlement, openDepositTracking,
                reservationStatusPolicy, clock);
    }
}
