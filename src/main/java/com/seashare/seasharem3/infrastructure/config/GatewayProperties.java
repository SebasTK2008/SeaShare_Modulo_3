package com.seashare.seasharem3.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
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
public class GatewayProperties {

    @NotBlank
    private String baseUrl;

    private String accessToken = "";

    private String webhookSecret = "";

    /** Cuenta de Mercado Pago del Propietario usada como collector_id en las dispersiones (UC10). */
    private long collectorId;

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

    public long getCollectorId() {
        return collectorId;
    }

    public void setCollectorId(long collectorId) {
        this.collectorId = collectorId;
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
