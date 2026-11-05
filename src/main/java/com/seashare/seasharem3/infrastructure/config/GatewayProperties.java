package com.seashare.seasharem3.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Propiedades del adaptador de la pasarela de pago: {@code seashare.gateway.*}.
 *
 * <p>Los timeouts por defecto son los de general-plan §7.2 para la pasarela (10 s). El token de
 * acceso y el secreto de firma se inyectan por entorno ({@code GATEWAY_ACCESS_TOKEN},
 * {@code GATEWAY_WEBHOOK_SECRET}); no hay valores sensibles en el repositorio.</p>
 */
@Validated
@ConfigurationProperties(prefix = "seashare.gateway")
public class GatewayProperties implements InitializingBean {

    @NotBlank
    private String baseUrl;

    private String accessToken = "";

    private String webhookSecret = "";

    /**
     * Modo del libro de liquidaciones (UC10). Solo {@code simulated} es valido en esta entrega:
     * el mercado de pagos real no esta implementado (D-UC10-10).
     */
    private String settlementMode = "simulated";

    /**
     * Si es {@code true} el access token y el secreto del webhook son obligatorios: lo activan los
     * perfiles {@code sandbox} y {@code prod}. En {@code dev} (simulador local) pueden ir vacios.
     */
    private boolean credentialsRequired = false;

    private Duration connectTimeout = Duration.ofSeconds(10);

    private Duration readTimeout = Duration.ofSeconds(10);

    private final CircuitBreaker circuitBreaker = new CircuitBreaker();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public String getSettlementMode() {
        return settlementMode;
    }

    public void setSettlementMode(String settlementMode) {
        this.settlementMode = settlementMode;
    }

    public boolean isCredentialsRequired() {
        return credentialsRequired;
    }

    public void setCredentialsRequired(boolean credentialsRequired) {
        this.credentialsRequired = credentialsRequired;
    }

    /**
     * Falla el arranque, nombrando la propiedad y la variable de entorno, si una propiedad conserva un
     * marcador sin resolver o si faltan credenciales requeridas.
     *
     * <p>Spring Boot enlaza un marcador sin resolver (por ejemplo {@code GATEWAY_ACCESS_TOKEN} entre
     * llaves y precedido de dolar) como texto literal en lugar de fallar: sin esta comprobacion, un
     * perfil {@code sandbox} o {@code prod} arrancaria con ese texto como token y solo fallaria al primer
     * cobro. Se verifica en {@code GatewayProfilesTest}.</p>
     */
    @Override
    public void afterPropertiesSet() {
        requireResolved("seashare.gateway.base-url", baseUrl);
        requireResolved("seashare.gateway.access-token", accessToken);
        requireResolved("seashare.gateway.webhook-secret", webhookSecret);
        requireResolved("seashare.gateway.settlement-mode", settlementMode);
        if (credentialsRequired) {
            requireText("seashare.gateway.access-token", "GATEWAY_ACCESS_TOKEN", accessToken);
            requireText("seashare.gateway.webhook-secret", "GATEWAY_WEBHOOK_SECRET", webhookSecret);
        }
    }

    private static void requireResolved(String property, String value) {
        if (value != null && value.contains("${")) {
            throw new IllegalStateException(property + " has an unresolved placeholder (" + value
                    + "): define the environment variable it refers to");
        }
    }

    private static void requireText(String property, String variable, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " is required in this profile (sandbox or prod): "
                    + "define a non-empty " + variable);
        }
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    /**
     * Umbrales del circuit breaker del adaptador de la pasarela: evita gastar los reintentos de la
     * cola contra un servicio caido. Fuente: general-plan §7.2 y D-20; UC05 plan T014.
     */
    public static class CircuitBreaker {

        private float failureRateThreshold = 50.0f;

        private int minimumNumberOfCalls = 5;

        private int slidingWindowSize = 10;

        private Duration waitDurationInOpenState = Duration.ofSeconds(10);

        private int permittedNumberOfCallsInHalfOpenState = 1;

        public float getFailureRateThreshold() {
            return failureRateThreshold;
        }

        public void setFailureRateThreshold(float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }

        public int getMinimumNumberOfCalls() {
            return minimumNumberOfCalls;
        }

        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) {
            this.minimumNumberOfCalls = minimumNumberOfCalls;
        }

        public int getSlidingWindowSize() {
            return slidingWindowSize;
        }

        public void setSlidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
        }

        public Duration getWaitDurationInOpenState() {
            return waitDurationInOpenState;
        }

        public void setWaitDurationInOpenState(Duration waitDurationInOpenState) {
            this.waitDurationInOpenState = waitDurationInOpenState;
        }

        public int getPermittedNumberOfCallsInHalfOpenState() {
            return permittedNumberOfCallsInHalfOpenState;
        }

        public void setPermittedNumberOfCallsInHalfOpenState(int permittedNumberOfCallsInHalfOpenState) {
            this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState;
        }
    }
}
