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
 * Los tres perfiles de la pasarela (general-plan §7.4): {@code dev} apunta al simulador local sin
 * credenciales, {@code dev,sandbox} exige credenciales de Mercado Pago y {@code prod} no tiene
 * defaults. Carga los {@code application*.properties} reales con las variables de entorno de la
 * maquina excluidas, para que el resultado no dependa de quien ejecute la prueba.
 */
class GatewayProfilesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> context.getEnvironment().getPropertySources()
                    .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME))
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void devPointsToTheLocalSimulatorWithoutCredentials() {
        contextRunner.withPropertyValues("spring.profiles.active=dev").run(context -> {
            assertThat(context).hasNotFailed();
            GatewayProperties properties = context.getBean(GatewayProperties.class);
            assertThat(properties.getBaseUrl()).isEqualTo("http://localhost:8082");
            assertThat(properties.getAccessToken()).isEmpty();
            assertThat(properties.getWebhookSecret()).isEqualTo("dev-webhook-secret");
            assertThat(properties.getSettlementMode()).isEqualTo("simulated");
        });
    }

    @Test
    void devNeverDefaultsToMercadoPago() {
        contextRunner.withPropertyValues("spring.profiles.active=dev").run(context -> assertThat(
                context.getBean(GatewayProperties.class).getBaseUrl()).doesNotContain("mercadopago"));
    }

    @Test
    void sandboxOverridesDevWithMercadoPagoAndTheProvidedCredentials() {
        contextRunner.withPropertyValues("spring.profiles.active=dev,sandbox",
                "GATEWAY_ACCESS_TOKEN=TEST-token", "GATEWAY_WEBHOOK_SECRET=secret-from-panel").run(context -> {
                    assertThat(context).hasNotFailed();
                    GatewayProperties properties = context.getBean(GatewayProperties.class);
                    assertThat(properties.getBaseUrl()).isEqualTo("https://api.mercadopago.com");
                    assertThat(properties.getAccessToken()).isEqualTo("TEST-token");
                    assertThat(properties.getWebhookSecret()).isEqualTo("secret-from-panel");
                    assertThat(properties.getSettlementMode()).isEqualTo("simulated");
                });
    }

    @Test
    void sandboxDoesNotStartWithoutTheAccessToken() {
        contextRunner.withPropertyValues("spring.profiles.active=dev,sandbox",
                "GATEWAY_WEBHOOK_SECRET=secret-from-panel").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void sandboxDoesNotStartWithoutTheWebhookSecret() {
        contextRunner.withPropertyValues("spring.profiles.active=dev,sandbox", "GATEWAY_ACCESS_TOKEN=TEST-token")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void sandboxDoesNotStartWithBlankCredentials() {
        contextRunner.withPropertyValues("spring.profiles.active=dev,sandbox", "GATEWAY_ACCESS_TOKEN=",
                "GATEWAY_WEBHOOK_SECRET=secret-from-panel").run(context -> assertThat(context).hasFailed());
        contextRunner.withPropertyValues("spring.profiles.active=dev,sandbox", "GATEWAY_ACCESS_TOKEN=TEST-token",
                "GATEWAY_WEBHOOK_SECRET=  ").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void prodDoesNotStartWithBlankCredentials() {
        contextRunner.withPropertyValues("spring.profiles.active=prod",
                "GATEWAY_BASE_URL=https://api.mercadopago.com", "GATEWAY_ACCESS_TOKEN=",
                "GATEWAY_WEBHOOK_SECRET=prod-secret", "GATEWAY_SETTLEMENT_MODE=simulated")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failureMessageNamesTheMissingCredential() {
        contextRunner.withPropertyValues("spring.profiles.active=dev,sandbox",
                "GATEWAY_WEBHOOK_SECRET=secret-from-panel").run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining("GATEWAY_ACCESS_TOKEN"));
    }

    @Test
    void prodBindsOnlyWhatTheEnvironmentProvides() {
        contextRunner.withPropertyValues("spring.profiles.active=prod",
                "GATEWAY_BASE_URL=https://api.mercadopago.com", "GATEWAY_ACCESS_TOKEN=APP_USR-token",
                "GATEWAY_WEBHOOK_SECRET=prod-secret", "GATEWAY_SETTLEMENT_MODE=simulated").run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(GatewayProperties.class).getBaseUrl())
                            .isEqualTo("https://api.mercadopago.com");
                });
    }

    @Test
    void prodDoesNotStartWhenAnyGatewayVariableIsMissing() {
        String[] all = {"GATEWAY_BASE_URL=https://api.mercadopago.com", "GATEWAY_ACCESS_TOKEN=APP_USR-token",
                "GATEWAY_WEBHOOK_SECRET=prod-secret", "GATEWAY_SETTLEMENT_MODE=simulated"};
        for (int missing = 0; missing < all.length; missing++) {
            String[] properties = new String[all.length];
            properties[0] = "spring.profiles.active=prod";
            int next = 1;
            for (int i = 0; i < all.length; i++) {
                if (i != missing) {
                    properties[next++] = all[i];
                }
            }
            String omitted = all[missing].substring(0, all[missing].indexOf('='));
            contextRunner.withPropertyValues(properties).run(context -> assertThat(context)
                    .as("prod must not start without %s", omitted).hasFailed());
        }
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
