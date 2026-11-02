package com.seashare.seasharem3.infrastructure.config;

import com.seashare.seasharem3.application.port.in.GatewayWebhookHandler;
import com.seashare.seasharem3.application.port.out.ChargeGatewayPort;
import com.seashare.seasharem3.application.port.out.ChargeCommandPublisher;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.application.port.out.ChargeRecordQueryPort;
import com.seashare.seasharem3.application.port.out.ChargeRecordRepository;
import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.application.port.out.GatewayNotificationPublisher;
import com.seashare.seasharem3.application.port.out.ReservationInformationRepository;
import com.seashare.seasharem3.application.service.ChargePaymentWebhookHandler;
import com.seashare.seasharem3.application.service.GatewayWebhookDispatcherService;
import com.seashare.seasharem3.application.service.ReconcileChargeIntentsService;
import com.seashare.seasharem3.application.service.RegisterChargeResultService;
import com.seashare.seasharem3.application.service.SendChargeToGatewayService;
import com.seashare.seasharem3.infrastructure.adapter.out.gateway.MercadoPagoChargeAdapter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

/**
 * Ensambla UC05 manteniendo {@code application} libre de Spring: los servicios se construyen aqui
 * porque un adaptador de salida no puede depender de {@code application.service} (regla 3 de
 * general-plan §3.4).
 *
 * <p>El adaptador de la pasarela se registra aqui y no en {@code adapter.out}: el bean construye el
 * servicio de envío, que vive en la capa de aplicación, y la regla 3 lo prohibe en el adaptador.</p>
 *
 * <p>Fuente: UC05 plan T006, T012, T014, T018 y T023; general-plan §3.4.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayProperties.class)
public class ChargeConfiguration {

    @Bean
    public ChargeGatewayPort chargeGatewayPort(GatewayProperties properties) {
        return new MercadoPagoChargeAdapter(properties);
    }

    @Bean
    public RegisterChargeResultService registerChargeResultService(
            ChargeIntentRepository chargeIntentRepository,
            ChargeRecordRepository chargeRecordRepository,
            ChargeRecordQueryPort chargeRecordQueryPort,
            ReservationInformationRepository reservationInformationRepository,
            FailureRecorderPort failureRecorderPort,
            Clock instantClock) {
        return new RegisterChargeResultService(chargeIntentRepository, chargeRecordRepository, chargeRecordQueryPort,
                reservationInformationRepository, failureRecorderPort, instantClock);
    }

    @Bean
    public SendChargeToGatewayService sendChargeToGatewayService(
            ChargeIntentRepository chargeIntentRepository,
            ChargeGatewayPort chargeGatewayPort,
            RegisterChargeResultService registerChargeResultService,
            FailureRecorderPort failureRecorderPort) {
        return new SendChargeToGatewayService(chargeIntentRepository, chargeGatewayPort,
                registerChargeResultService, failureRecorderPort);
    }

    @Bean
    public ReconcileChargeIntentsService reconcileChargeIntentsService(
            ChargeIntentRepository chargeIntentRepository,
            ChargeRecordQueryPort chargeRecordQueryPort,
            ChargeGatewayPort chargeGatewayPort,
            RegisterChargeResultService registerChargeResultService,
            FailureRecorderPort failureRecorderPort,
            ChargeCommandPublisher chargeCommandPublisher) {
        return new ReconcileChargeIntentsService(chargeIntentRepository, chargeRecordQueryPort, chargeGatewayPort,
                registerChargeResultService, failureRecorderPort, chargeCommandPublisher);
    }

    @Bean
    public ChargePaymentWebhookHandler chargePaymentWebhookHandler(
            ChargeIntentRepository chargeIntentRepository,
            ChargeGatewayPort chargeGatewayPort,
            RegisterChargeResultService registerChargeResultService,
            FailureRecorderPort failureRecorderPort) {
        return new ChargePaymentWebhookHandler(chargeIntentRepository, chargeGatewayPort,
                registerChargeResultService, failureRecorderPort);
    }

    @Bean
    public GatewayWebhookDispatcherService gatewayWebhookDispatcherService(
            List<GatewayWebhookHandler> handlers,
            GatewayNotificationPublisher notificationPublisher,
            FailureRecorderPort failureRecorderPort) {
        return new GatewayWebhookDispatcherService(handlers, notificationPublisher, failureRecorderPort);
    }
}
