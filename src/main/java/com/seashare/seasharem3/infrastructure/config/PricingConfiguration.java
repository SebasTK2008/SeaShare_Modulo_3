package com.seashare.seasharem3.infrastructure.config;

import com.seashare.seasharem3.domain.service.DynamicRatePolicy;
import com.seashare.seasharem3.domain.service.HighSeasonCalendar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring neutral del dominio de pricing compartido: calendario de temporada alta y política dinámica.
 * No depende de la configuración de Flota, de modo que UC11 puede consumir el calendario sin arrastrar
 * las propiedades de UC02.
 * Fuente: UC11 plan T029/T031; B5 de la revisión.
 */
@Configuration(proxyBeanMethods = false)
public class PricingConfiguration {

    @Bean
    public HighSeasonCalendar highSeasonCalendar() {
        return new HighSeasonCalendar();
    }

    @Bean
    public DynamicRatePolicy dynamicRatePolicy(HighSeasonCalendar highSeasonCalendar) {
        return new DynamicRatePolicy(highSeasonCalendar);
    }
}
