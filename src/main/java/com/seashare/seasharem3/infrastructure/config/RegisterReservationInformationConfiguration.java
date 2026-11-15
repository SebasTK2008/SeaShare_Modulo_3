package com.seashare.seasharem3.infrastructure.config;

import com.seashare.seasharem3.application.port.in.ProvideBaseRateUseCase;
import com.seashare.seasharem3.application.port.in.RegisterReservationInformationUseCase;
import com.seashare.seasharem3.application.port.out.ReservationInformationRepository;
import com.seashare.seasharem3.application.service.RegisterReservationInformationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ensambla UC03 manteniendo {@code application} libre de Spring.
 *
 * <p>Vive en {@code infrastructure.config} porque construye un servicio de aplicación; el listener
 * y el adaptador de persistencia siguen siendo adaptadores. Fuente: UC03 plan T017 y general-plan
 * §3.4.</p>
 */
@Configuration(proxyBeanMethods = false)
public class RegisterReservationInformationConfiguration {

    @Bean
    public RegisterReservationInformationUseCase registerReservationInformationUseCase(
            ProvideBaseRateUseCase provideBaseRateUseCase,
            ReservationInformationRepository reservationInformationRepository) {
        return new RegisterReservationInformationService(provideBaseRateUseCase,
                reservationInformationRepository);
    }
}
