package com.seashare.seasharem3.infrastructure.adapter.out.fleet;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que la URL de Flota sea obligatoria y que los defaults operativos se apliquen.
 * En producción {@code application-prod.properties} aporta {@code ${FLEET_BASE_URL}} sin default;
 * esta prueba evita que un default Java o una URL vacía vuelvan a colarse.
 */
class FleetRatePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void failsWhenBaseUrlIsMissing() {
        contextRunner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenBaseUrlIsBlank() {
        contextRunner.withPropertyValues("seashare.fleet.base-url=   ")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void bindsBaseUrlAndOperationalDefaults() {
        contextRunner.withPropertyValues("seashare.fleet.base-url=http://localhost:8081")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    FleetRateProperties properties = context.getBean(FleetRateProperties.class);
                    assertThat(properties.getBaseUrl()).isEqualTo("http://localhost:8081");
                    assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(1));
                    assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(1));
                    assertThat(properties.getRetry().getMaxAttempts()).isEqualTo(2);
                    assertThat(properties.getRetry().getWaitDuration()).isEqualTo(Duration.ofMillis(100));
                    assertThat(properties.getCircuitBreaker().getMinimumNumberOfCalls()).isEqualTo(5);
                    assertThat(properties.getCircuitBreaker().getSlidingWindowSize()).isEqualTo(10);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(FleetRateProperties.class)
    static class PropertiesConfiguration {

        @Bean
        static LocalValidatorFactoryBean validator() {
            return new LocalValidatorFactoryBean();
        }
    }
}
