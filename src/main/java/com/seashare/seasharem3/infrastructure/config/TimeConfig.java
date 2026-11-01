package com.seashare.seasharem3.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Reloj de instantes en UTC (los instantes se persisten y publican en UTC; las fechas de negocio
 * usan {@code ClockPort} en {@code seashare.timezone}).
 * Fuente: general-plan D-12 y §7.4. [CONV] núcleo compartido.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ZoneId businessZoneId(@Value("${seashare.timezone:America/Bogota}") String timezone) {
        return ZoneId.of(timezone);
    }
}
