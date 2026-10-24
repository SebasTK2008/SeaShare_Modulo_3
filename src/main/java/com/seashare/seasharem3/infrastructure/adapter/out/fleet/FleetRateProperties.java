package com.seashare.seasharem3.infrastructure.adapter.out.fleet;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Propiedades del adaptador de Flota: {@code seashare.fleet.*}.
 *
 * <p>{@code base-url} no tiene valor por defecto en Java: en {@code dev} se aporta el mock local
 * desde {@code application-dev.properties}; en {@code prod} se exige {@code FLEET_BASE_URL} sin
 * default y la validación {@link NotBlank} impide arrancar con una URL vacía.</p>
 */
@Validated
@ConfigurationProperties(prefix = "seashare.fleet")
public class FleetRateProperties {

    @NotBlank
    private String baseUrl;

    private Duration connectTimeout = Duration.ofSeconds(1);

    private Duration readTimeout = Duration.ofSeconds(1);

    private final Retry retry = new Retry();

    private final CircuitBreaker circuitBreaker = new CircuitBreaker();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
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

    public Retry getRetry() {
        return retry;
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    /** Reintento corto ante fallas transitorias de Flota. Fuente: general-plan §7.2. */
    public static class Retry {

        private int maxAttempts = 2;

        private Duration waitDuration = Duration.ofMillis(100);

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getWaitDuration() {
            return waitDuration;
        }

        public void setWaitDuration(Duration waitDuration) {
            this.waitDuration = waitDuration;
        }
    }

    /** Umbrales del circuit breaker del adaptador de Flota. Fuente: general-plan §7.2. */
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
