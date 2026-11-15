package com.seashare.seasharem3.infrastructure.config;

import com.seashare.seasharem3.application.port.in.OpenDepositTrackingUseCase;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.application.port.out.ChargeRecordQueryPort;
import com.seashare.seasharem3.application.port.out.CommissionRecordRepository;
import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.application.port.out.FinancialParametersRepository;
import com.seashare.seasharem3.application.port.out.ReservationInformationRepository;
import com.seashare.seasharem3.application.port.out.SettlementCommandPublisher;
import com.seashare.seasharem3.application.port.out.SettlementGatewayPort;
import com.seashare.seasharem3.application.port.out.SettlementIntentRepository;
import com.seashare.seasharem3.application.port.out.SettlementRecordRepository;
import com.seashare.seasharem3.application.service.ReconcileSettlementIntentsService;
import com.seashare.seasharem3.application.service.RegisterSettlementResultService;
import com.seashare.seasharem3.application.service.RequestSettlementService;
import com.seashare.seasharem3.application.service.SendSettlementToGatewayService;
import com.seashare.seasharem3.domain.service.SettlementCalculator;
import com.seashare.seasharem3.infrastructure.adapter.out.gateway.SimulatedSettlementGatewayAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Ensambla UC10 manteniendo la capa de aplicacion libre de Spring.
 *
 * <p>La liquidacion usa el libro interno simulado: {@code seashare.gateway.settlement-mode} solo
 * admite {@code simulated} en esta entrega y cualquier otro valor impide arrancar (D-UC10-10).</p>
 */
@Configuration(proxyBeanMethods = false)
public class SettlementConfiguration {

    private static final String SIMULATED_MODE = "simulated";

    @Bean
    public SettlementCalculator settlementCalculator() {
        return new SettlementCalculator();
    }

    @Bean
    public SettlementGatewayPort settlementGatewayPort(GatewayProperties properties) {
        String mode = properties.getSettlementMode();
        if (mode == null || !SIMULATED_MODE.equalsIgnoreCase(mode.trim())) {
            throw new IllegalStateException("seashare.gateway.settlement-mode must be '" + SIMULATED_MODE
                    + "': the real marketplace settlement is not implemented in this delivery");
        }
        return new SimulatedSettlementGatewayAdapter();
    }

    @Bean
    public RequestSettlementService requestSettlementService(SettlementIntentRepository settlementIntentRepository,
            ChargeIntentRepository chargeIntentRepository, ChargeRecordQueryPort chargeRecordQueryPort,
            ReservationInformationRepository reservationInformationRepository,
            FinancialParametersRepository financialParametersRepository, SettlementCalculator settlementCalculator,
            SettlementCommandPublisher settlementCommandPublisher, OpenDepositTrackingUseCase openDepositTracking,
            FailureRecorderPort failureRecorder, Clock instantClock) {
        return new RequestSettlementService(settlementIntentRepository, chargeIntentRepository,
                chargeRecordQueryPort, reservationInformationRepository, financialParametersRepository,
                settlementCalculator, settlementCommandPublisher, openDepositTracking, failureRecorder, instantClock);
    }

    @Bean
    public RegisterSettlementResultService registerSettlementResultService(
            SettlementIntentRepository settlementIntentRepository,
            SettlementRecordRepository settlementRecordRepository,
            CommissionRecordRepository commissionRecordRepository, ChargeIntentRepository chargeIntentRepository,
            ChargeRecordQueryPort chargeRecordQueryPort,
            ReservationInformationRepository reservationInformationRepository, FailureRecorderPort failureRecorder,
            Clock instantClock) {
        return new RegisterSettlementResultService(settlementIntentRepository, settlementRecordRepository,
                commissionRecordRepository, chargeIntentRepository, chargeRecordQueryPort,
                reservationInformationRepository, failureRecorder, instantClock);
    }

    @Bean
    public SendSettlementToGatewayService sendSettlementToGatewayService(
            SettlementIntentRepository settlementIntentRepository,
            SettlementGatewayPort settlementGatewayPort,
            RegisterSettlementResultService registerSettlementResult, FailureRecorderPort failureRecorder) {
        return new SendSettlementToGatewayService(settlementIntentRepository, settlementGatewayPort,
                registerSettlementResult, failureRecorder);
    }

    @Bean
    public ReconcileSettlementIntentsService reconcileSettlementIntentsService(
            SettlementIntentRepository settlementIntentRepository,
            SettlementRecordRepository settlementRecordRepository,
            RegisterSettlementResultService registerSettlementResult, FailureRecorderPort failureRecorder,
            SettlementCommandPublisher settlementCommandPublisher) {
        return new ReconcileSettlementIntentsService(settlementIntentRepository, settlementRecordRepository,
                registerSettlementResult, failureRecorder, settlementCommandPublisher);
    }
}
