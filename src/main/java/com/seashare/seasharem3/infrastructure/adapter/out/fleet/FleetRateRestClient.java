package com.seashare.seasharem3.infrastructure.adapter.out.fleet;

import com.seashare.seasharem3.application.port.out.FleetRatePort;
import com.seashare.seasharem3.domain.exception.BaseRateException;
import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Adaptador HTTP de UC02 hacia el Sistema de Gestión de Flota.
 *
 * <p>Hace una única consulta batch, con timeout de conexión/lectura, un reintento corto y un
 * circuit breaker. Cualquier falla de transporte, timeout, respuesta inválida o tarifa no
 * numérica/negativa/cero se traduce a {@code BaseRateException(FLEET_UNAVAILABLE)}. Una tarifa
 * nula/ausente se omite del mapa para que el servicio registre {@code BASE_RATE_NOT_AVAILABLE}.</p>
 */
public class FleetRateRestClient implements FleetRatePort {

    private static final String BASE_RATES_PATH = "/api/v1/fleet/base-rates";
    private static final String DEPENDENCY_NAME = "fleetRate";

    private final RestClient restClient;
    private final FleetRateMapper mapper;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public FleetRateRestClient(FleetRateProperties properties, FleetRateMapper mapper) {
        Objects.requireNonNull(properties, "properties");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.restClient = buildRestClient(properties);
        this.retry = buildRetry(properties.getRetry());
        this.circuitBreaker = buildCircuitBreaker(properties.getCircuitBreaker());
    }

    @Override
    public Map<BoatId, BaseRate> findBaseRates(Set<BoatId> boatIds) {
        Objects.requireNonNull(boatIds, "boatIds");
        if (boatIds.isEmpty()) {
            return Map.of();
        }

        Supplier<Map<BoatId, BaseRate>> call = () -> execute(boatIds);
        Supplier<Map<BoatId, BaseRate>> guarded = Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(circuitBreaker, call));
        try {
            return guarded.get();
        } catch (BaseRateException exception) {
            throw exception;
        } catch (CallNotPermittedException exception) {
            throw fleetUnavailable("Fleet circuit breaker is open", exception);
        } catch (RuntimeException exception) {
            throw fleetUnavailable("Fleet base rate query failed", exception);
        }
    }

    private Map<BoatId, BaseRate> execute(Set<BoatId> boatIds) {
        try {
            FleetBaseRatesResponseDto response = restClient.post()
                    .uri(BASE_RATES_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(mapper.toRequest(boatIds))
                    .retrieve()
                    .body(FleetBaseRatesResponseDto.class);
            return mapper.toDomain(response);
        } catch (BaseRateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw fleetUnavailable("Fleet base rate query failed", exception);
        }
    }

    private static RestClient buildRestClient(FleetRateProperties properties) {
        String baseUrl = Objects.requireNonNull(properties.getBaseUrl(), "baseUrl");
        if (baseUrl.isBlank()) {
            throw new IllegalArgumentException("seashare.fleet.base-url must be configured");
        }
        Duration connectTimeout = Objects.requireNonNull(properties.getConnectTimeout(), "connectTimeout");
        Duration readTimeout = Objects.requireNonNull(properties.getReadTimeout(), "readTimeout");

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    private static Retry buildRetry(FleetRateProperties.Retry properties) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(properties.getMaxAttempts())
                .waitDuration(properties.getWaitDuration())
                .retryExceptions(BaseRateException.class)
                .build();
        return Retry.of(DEPENDENCY_NAME, config);
    }

    private static CircuitBreaker buildCircuitBreaker(FleetRateProperties.CircuitBreaker properties) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(properties.getFailureRateThreshold())
                .minimumNumberOfCalls(properties.getMinimumNumberOfCalls())
                .slidingWindowSize(properties.getSlidingWindowSize())
                .waitDurationInOpenState(properties.getWaitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(
                        properties.getPermittedNumberOfCallsInHalfOpenState())
                .build();
        return CircuitBreaker.of(DEPENDENCY_NAME, config);
    }

    private static BaseRateException fleetUnavailable(String message, Throwable cause) {
        return new BaseRateException(BaseRateException.BaseRateFailureReason.FLEET_UNAVAILABLE,
                message, cause);
    }
}
