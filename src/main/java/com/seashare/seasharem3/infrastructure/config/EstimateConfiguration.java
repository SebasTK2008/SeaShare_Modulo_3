package com.seashare.seasharem3.infrastructure.config;

import com.seashare.seasharem3.application.port.in.ProvideBaseRateUseCase;
import com.seashare.seasharem3.application.port.out.ClockPort;
import com.seashare.seasharem3.application.port.out.FinancialParametersRepository;
import com.seashare.seasharem3.application.service.EstimateBatchService;
import com.seashare.seasharem3.application.service.EstimateSingleService;
import com.seashare.seasharem3.domain.service.PricingCalculator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ensambla UC01 manteniendo {@code application} libre de Spring (general-plan §3.4 regla 2).
 *
 * <p>Vive en {@code infrastructure.config} por la misma razon que {@link FleetRateConfiguration}:
 * construye servicios de aplicacion, y un adaptador no debe depender de {@code application.service}.
 * El limite de lote se toma de {@link EstimateProperties}, no de una constante.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EstimateProperties.class)
public class EstimateConfiguration {

    @Bean
    public PricingCalculator pricingCalculator() {
        return new PricingCalculator();
    }

    @Bean
    public EstimateBatchService estimateBatchService(ProvideBaseRateUseCase provideBaseRateUseCase,
            FinancialParametersRepository financialParametersRepository,
            ClockPort clockPort,
            PricingCalculator pricingCalculator,
            EstimateProperties properties) {
        return new EstimateBatchService(provideBaseRateUseCase, financialParametersRepository, clockPort,
                pricingCalculator, properties.getMaxBatchSize());
    }

    @Bean
    public EstimateSingleService estimateSingleService(ProvideBaseRateUseCase provideBaseRateUseCase,
            FinancialParametersRepository financialParametersRepository,
            ClockPort clockPort,
            PricingCalculator pricingCalculator) {
        return new EstimateSingleService(provideBaseRateUseCase, financialParametersRepository, clockPort,
                pricingCalculator);
    }
}
