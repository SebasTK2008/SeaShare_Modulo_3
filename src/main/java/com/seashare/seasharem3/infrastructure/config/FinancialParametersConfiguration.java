package com.seashare.seasharem3.infrastructure.config;

import com.seashare.seasharem3.application.port.out.ClockPort;
import com.seashare.seasharem3.application.port.out.FinancialParametersRepository;
import com.seashare.seasharem3.application.service.FinancialParametersService;
import com.seashare.seasharem3.domain.service.HighSeasonCalendar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Ensambla UC11 manteniendo {@code application} libre de Spring.
 * Fuente: general-plan §3.4-2 (application depende solo de domain).
 */
@Configuration(proxyBeanMethods = false)
public class FinancialParametersConfiguration {

    @Bean
    public FinancialParametersService financialParametersService(
            FinancialParametersRepository repository,
            HighSeasonCalendar highSeasonCalendar,
            ClockPort clock,
            Clock instantClock) {
        return new FinancialParametersService(repository, highSeasonCalendar, clock, instantClock);
    }
}

