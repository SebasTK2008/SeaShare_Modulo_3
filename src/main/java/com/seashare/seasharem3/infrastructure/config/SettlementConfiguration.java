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
import com.seashare.seasharem3.application.service.SettlementWebhookHandler;
import com.seashare.seasharem3.domain.service.SettlementCalculator;
import com.seashare.seasharem3.infrastructure.adapter.out.gateway.MercadoPagoSettlementAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Ensambla UC10 manteniendo la capa de aplicacion libre de Spring. */
@Configuration(proxyBeanMethods = false)
public class SettlementConfiguration {

    @Bean
    public SettlementCalculator settlementCalculator() {
        return new SettlementCalculator();
    }

    @Bean
    public SettlementGatewayPort settlementGatewayPort(GatewayProperties properties) {
        return new MercadoPagoSettlementAdapter(properties);
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
            SettlementIntentRepository settlementIntentRepository, ChargeIntentRepository chargeIntentRepository,
            SettlementGatewayPort settlementGatewayPort,
            RegisterSettlementResultService registerSettlementResult, FailureRecorderPort failureRecorder,
            GatewayProperties gatewayProperties) {
        return new SendSettlementToGatewayService(settlementIntentRepository, chargeIntentRepository,
                settlementGatewayPort, registerSettlementResult, failureRecorder,
                gatewayProperties.getCollectorId());
    }

    @Bean
    public ReconcileSettlementIntentsService reconcileSettlementIntentsService(
            SettlementIntentRepository settlementIntentRepository,
            SettlementRecordRepository settlementRecordRepository, SettlementGatewayPort settlementGatewayPort,
            RegisterSettlementResultService registerSettlementResult, FailureRecorderPort failureRecorder,
            SettlementCommandPublisher settlementCommandPublisher) {
        return new ReconcileSettlementIntentsService(settlementIntentRepository, settlementRecordRepository,
                settlementGatewayPort, registerSettlementResult, failureRecorder, settlementCommandPublisher);
    }

    @Bean
    public SettlementWebhookHandler settlementWebhookHandler(
            SettlementIntentRepository settlementIntentRepository, SettlementGatewayPort settlementGatewayPort,
            RegisterSettlementResultService registerSettlementResult, FailureRecorderPort failureRecorder) {
        return new SettlementWebhookHandler(settlementIntentRepository, settlementGatewayPort,
                registerSettlementResult, failureRecorder);
    }
}
