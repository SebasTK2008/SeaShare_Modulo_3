package com.seashare.seasharem3.infrastructure.config;

import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.application.port.out.FinancialParametersRepository;
import com.seashare.seasharem3.application.port.out.FleetRatePort;
import com.seashare.seasharem3.application.service.ProvideBaseRateService;
import com.seashare.seasharem3.domain.service.DynamicRatePolicy;
import com.seashare.seasharem3.domain.service.HighSeasonCalendar;
import com.seashare.seasharem3.infrastructure.adapter.out.fleet.FleetRateMapper;
import com.seashare.seasharem3.infrastructure.adapter.out.fleet.FleetRateProperties;
import com.seashare.seasharem3.infrastructure.adapter.out.fleet.FleetRateRestClient;
import org.mapstruct.factory.Mappers;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ensambla UC02 manteniendo {@code application} libre de Spring.
 *
 * <p>Vive en {@code infrastructure.config} y no en {@code adapter.out}: este bean construye
 * {@link ProvideBaseRateService} (capa de aplicación) y la regla 3 de arquitectura prohíbe que un
 * adaptador de salida dependa de {@code application.service}. El adaptador HTTP permanece en
 * {@code adapter.out.fleet} y solo implementa {@link FleetRatePort}.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FleetRateProperties.class)
public class FleetRateConfiguration {

    @Bean
    public FleetRateMapper fleetRateMapper() {
        return Mappers.getMapper(FleetRateMapper.class);
    }

    @Bean
    public FleetRatePort fleetRatePort(FleetRateProperties properties, FleetRateMapper mapper) {
        return new FleetRateRestClient(properties, mapper);
    }

    @Bean
    public HighSeasonCalendar highSeasonCalendar() {
        return new HighSeasonCalendar();
    }

    @Bean
    public DynamicRatePolicy dynamicRatePolicy(HighSeasonCalendar highSeasonCalendar) {
        return new DynamicRatePolicy(highSeasonCalendar);
    }

    @Bean
    public ProvideBaseRateService provideBaseRateService(FleetRatePort fleetRatePort,
            FinancialParametersRepository financialParametersRepository,
            DynamicRatePolicy dynamicRatePolicy,
            FailureRecorderPort failureRecorder) {
        return new ProvideBaseRateService(fleetRatePort, financialParametersRepository,
                dynamicRatePolicy, failureRecorder);
    }
}
