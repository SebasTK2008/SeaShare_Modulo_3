package com.seashare.seasharem3.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La pasarela en los perfiles reales: {@code dev} arranca sin credenciales y {@code prod} no arranca si
 * falta alguna variable. Spring Boot enlaza un {@code ${VARIABLE}} sin resolver como texto literal en lugar
 * de fallar; sin la validacion de {@link GatewayProperties}, prod arrancaria con ese texto como token.
 * Carga los {@code application*.properties} reales con las variables de entorno de la maquina excluidas,
 * para que el resultado no dependa de quien ejecute la prueba.
 */
class GatewayProfilesTest {

    private static final String[] PROD_VARIABLES = {"GATEWAY_BASE_URL=https://api.mercadopago.com",
        "GATEWAY_ACCESS_TOKEN=APP_USR-token", "GATEWAY_WEBHOOK_SECRET=prod-secret",
        "GATEWAY_SETTLEMENT_MODE=simulated"};

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> context.getEnvironment().getPropertySources()
                    .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME))
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void devStartsWithoutCredentials() {
        contextRunner.withPropertyValues("spring.profiles.active=dev").run(context -> {
            assertThat(context).hasNotFailed();
            GatewayProperties properties = context.getBean(GatewayProperties.class);
            assertThat(properties.getBaseUrl()).isEqualTo("https://api.mercadopago.com");
            assertThat(properties.getAccessToken()).isEmpty();
            assertThat(properties.getSettlementMode()).isEqualTo("simulated");
        });
    }

    @Test
    void prodBindsWhatTheEnvironmentProvides() {
        contextRunner.withPropertyValues(withProfile("prod", PROD_VARIABLES)).run(context -> {
            assertThat(context).hasNotFailed();
            GatewayProperties properties = context.getBean(GatewayProperties.class);
            assertThat(properties.getAccessToken()).isEqualTo("APP_USR-token");
            assertThat(properties.getWebhookSecret()).isEqualTo("prod-secret");
        });
    }

    @Test
    void prodDoesNotStartWhenAnyGatewayVariableIsMissing() {
        for (int missing = 0; missing < PROD_VARIABLES.length; missing++) {
            String omitted = PROD_VARIABLES[missing];
            String[] provided = java.util.Arrays.stream(PROD_VARIABLES).filter(variable -> !variable.equals(omitted))
                    .toArray(String[]::new);
            contextRunner.withPropertyValues(withProfile("prod", provided)).run(context -> assertThat(context)
                    .as("prod must not start without %s", omitted).hasFailed());
        }
    }

    @Test
    void prodDoesNotStartWithBlankCredentials() {
        contextRunner.withPropertyValues(withProfile("prod", "GATEWAY_BASE_URL=https://api.mercadopago.com",
                "GATEWAY_ACCESS_TOKEN=", "GATEWAY_WEBHOOK_SECRET=prod-secret", "GATEWAY_SETTLEMENT_MODE=simulated"))
                .run(context -> assertThat(context).hasFailed());
        contextRunner.withPropertyValues(withProfile("prod", "GATEWAY_BASE_URL=https://api.mercadopago.com",
                "GATEWAY_ACCESS_TOKEN=APP_USR-token", "GATEWAY_WEBHOOK_SECRET=  ",
                "GATEWAY_SETTLEMENT_MODE=simulated")).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void theFailureNamesTheMissingVariable() {
        contextRunner.withPropertyValues(withProfile("prod", "GATEWAY_BASE_URL=https://api.mercadopago.com",
                "GATEWAY_WEBHOOK_SECRET=prod-secret", "GATEWAY_SETTLEMENT_MODE=simulated"))
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining("GATEWAY_ACCESS_TOKEN"));
    }

    private static String[] withProfile(String profile, String... variables) {
        String[] properties = new String[variables.length + 1];
        properties[0] = "spring.profiles.active=" + profile;
        System.arraycopy(variables, 0, properties, 1, variables.length);
        return properties;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(GatewayProperties.class)
    static class PropertiesConfiguration {

        @Bean
        static LocalValidatorFactoryBean validator() {
            return new LocalValidatorFactoryBean();
        }
    }
}
