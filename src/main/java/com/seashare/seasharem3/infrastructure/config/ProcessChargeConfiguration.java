package com.seashare.seasharem3.infrastructure.config;

import com.seashare.seasharem3.application.port.out.ChargeCommandPublisher;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.application.port.out.ReservationInformationRepository;
import com.seashare.seasharem3.application.service.ProcessChargeService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Ensambla UC05 manteniendo {@code application} libre de Spring; el bean es necesario para que la
 * anotación transaccional de {@link ProcessChargeService} sea efectiva (la intencion y el mensaje
 * del outbox se confirman en la misma transaccion).
 * Fuente: UC05 plan T008 y regla 4; general-plan §3.4-2 y D-08.
 */
@Configuration(proxyBeanMethods = false)
public class ProcessChargeConfiguration {

    @Bean
    public ProcessChargeService processChargeService(
            ReservationInformationRepository reservationInformationRepository,
            ChargeIntentRepository chargeIntentRepository,
            ChargeCommandPublisher chargeCommandPublisher,
            Clock instantClock) {
        return new ProcessChargeService(reservationInformationRepository, chargeIntentRepository,
                chargeCommandPublisher, instantClock);
    }
}
