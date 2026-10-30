package com.seashare.seasharem3.infrastructure.config;

import com.seashare.seasharem3.application.port.in.OpenDepositTrackingUseCase;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.application.port.out.ChargeRecordQueryPort;
import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.application.port.out.RefundCommandPublisher;
import com.seashare.seasharem3.application.port.out.RefundGatewayPort;
import com.seashare.seasharem3.application.port.out.RefundIntentRepository;
import com.seashare.seasharem3.application.port.out.RefundRecordRepository;
import com.seashare.seasharem3.application.port.out.ReservationInformationRepository;
import com.seashare.seasharem3.application.service.ReconcileRefundIntentsService;
import com.seashare.seasharem3.application.service.RefundWebhookHandler;
import com.seashare.seasharem3.application.service.RegisterRefundResultService;
import com.seashare.seasharem3.application.service.RequestRefundService;
import com.seashare.seasharem3.application.service.SendRefundToGatewayService;
import com.seashare.seasharem3.domain.service.RefundCalculator;
import com.seashare.seasharem3.infrastructure.adapter.out.gateway.MercadoPagoRefundAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Ensambla UC09 manteniendo la capa de aplicacion libre de Spring. */
@Configuration(proxyBeanMethods = false)
public class RefundConfiguration {

    @Bean
    public RefundCalculator refundCalculator() {
        return new RefundCalculator();
    }

    @Bean
    public RefundGatewayPort refundGatewayPort(GatewayProperties properties) {
        return new MercadoPagoRefundAdapter(properties);
    }

    @Bean
    public RequestRefundService requestRefundService(RefundIntentRepository refundIntentRepository,
            ChargeIntentRepository chargeIntentRepository, ChargeRecordQueryPort chargeRecordQueryPort,
            ReservationInformationRepository reservationInformationRepository, RefundCalculator refundCalculator,
            RefundCommandPublisher refundCommandPublisher, OpenDepositTrackingUseCase openDepositTracking,
            FailureRecorderPort failureRecorder, Clock instantClock) {
        return new RequestRefundService(refundIntentRepository, chargeIntentRepository, chargeRecordQueryPort,
                reservationInformationRepository, refundCalculator, refundCommandPublisher, openDepositTracking,
                failureRecorder, instantClock);
    }

    @Bean
    public RegisterRefundResultService registerRefundResultService(RefundIntentRepository refundIntentRepository,
            RefundRecordRepository refundRecordRepository, ChargeIntentRepository chargeIntentRepository,
            ChargeRecordQueryPort chargeRecordQueryPort,
            ReservationInformationRepository reservationInformationRepository, FailureRecorderPort failureRecorder,
            Clock instantClock) {
        return new RegisterRefundResultService(refundIntentRepository, refundRecordRepository,
                chargeIntentRepository, chargeRecordQueryPort, reservationInformationRepository, failureRecorder,
                instantClock);
    }

    @Bean
    public SendRefundToGatewayService sendRefundToGatewayService(RefundIntentRepository refundIntentRepository,
            ChargeIntentRepository chargeIntentRepository, RefundGatewayPort refundGatewayPort,
            RegisterRefundResultService registerRefundResult, FailureRecorderPort failureRecorder) {
        return new SendRefundToGatewayService(refundIntentRepository, chargeIntentRepository, refundGatewayPort,
                registerRefundResult, failureRecorder);
    }

    @Bean
    public ReconcileRefundIntentsService reconcileRefundIntentsService(
            RefundIntentRepository refundIntentRepository, RefundRecordRepository refundRecordRepository,
            ChargeIntentRepository chargeIntentRepository, RefundGatewayPort refundGatewayPort,
            RegisterRefundResultService registerRefundResult, FailureRecorderPort failureRecorder) {
        return new ReconcileRefundIntentsService(refundIntentRepository, refundRecordRepository,
                chargeIntentRepository, refundGatewayPort, registerRefundResult, failureRecorder);
    }

    @Bean
    public RefundWebhookHandler refundWebhookHandler(RefundIntentRepository refundIntentRepository,
            ChargeIntentRepository chargeIntentRepository, RefundGatewayPort refundGatewayPort,
            RegisterRefundResultService registerRefundResult, FailureRecorderPort failureRecorder) {
        return new RefundWebhookHandler(refundIntentRepository, chargeIntentRepository, refundGatewayPort,
                registerRefundResult, failureRecorder);
    }
}
