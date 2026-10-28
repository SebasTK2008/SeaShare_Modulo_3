package com.seashare.seasharem3.infrastructure.config;

import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.application.port.out.FinancialParametersRepository;
import com.seashare.seasharem3.application.port.out.ReservationInformationRepository;
import com.seashare.seasharem3.application.port.out.ReservationLockPort;
import com.seashare.seasharem3.application.service.CalculateReservationValueService;
import com.seashare.seasharem3.domain.service.ReservationValueCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Ensambla UC04 manteniendo {@code application} libre de Spring y construyendo el calculator puro
 * del dominio. Fuente: UC04 plan T011 y general-plan §3.4-2.
 */
@Configuration(proxyBeanMethods = false)
public class CalculateReservationValueConfiguration {

    @Bean
    public CalculateReservationValueService calculateReservationValueService(
            ReservationInformationRepository reservationInformationRepository,
            FinancialParametersRepository financialParametersRepository,
            FailureRecorderPort failureRecorderPort,
            ReservationLockPort reservationLockPort,
            Clock instantClock) {
        return new CalculateReservationValueService(reservationInformationRepository,
                financialParametersRepository, failureRecorderPort, reservationLockPort,
                new ReservationValueCalculator(), instantClock);
    }
}
