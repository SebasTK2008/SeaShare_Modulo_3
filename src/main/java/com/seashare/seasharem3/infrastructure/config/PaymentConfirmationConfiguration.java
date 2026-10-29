package com.seashare.seasharem3.infrastructure.config;

import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.application.service.GetPaymentConfirmationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ensambla UC06 manteniendo {@code application} libre de Spring. El servicio se construye como bean
 * para que {@code @Transactional(readOnly = true)} tenga efecto.
 *
 * <p>Fuente: UC06 plan T008; patrón de {@code ChargeConfiguration} (general-plan §3.4).</p>
 */
@Configuration(proxyBeanMethods = false)
public class PaymentConfirmationConfiguration {

    @Bean
    public GetPaymentConfirmationService getPaymentConfirmationService(
            ChargeIntentRepository chargeIntentRepository) {
        return new GetPaymentConfirmationService(chargeIntentRepository);
    }
}
