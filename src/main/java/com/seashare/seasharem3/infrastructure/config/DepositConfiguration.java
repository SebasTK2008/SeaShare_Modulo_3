package com.seashare.seasharem3.infrastructure.config;

import com.seashare.seasharem3.application.port.out.DepositDispositionRepository;
import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.application.port.out.ReservationLockPort;
import com.seashare.seasharem3.application.service.OpenDepositTrackingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Ensambla el UC08 minimo (desenlace del deposito) sin que la aplicacion dependa de Spring. */
@Configuration(proxyBeanMethods = false)
public class DepositConfiguration {

    @Bean
    public OpenDepositTrackingService openDepositTrackingService(DepositDispositionRepository repository,
            ReservationLockPort reservationLock, FailureRecorderPort failureRecorder, Clock instantClock) {
        return new OpenDepositTrackingService(repository, reservationLock, failureRecorder, instantClock);
    }
}
