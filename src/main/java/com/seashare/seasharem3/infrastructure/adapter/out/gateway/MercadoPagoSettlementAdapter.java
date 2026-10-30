package com.seashare.seasharem3.infrastructure.adapter.out.gateway;

import com.seashare.seasharem3.application.dto.GatewaySettlementRequest;
import com.seashare.seasharem3.application.dto.GatewaySettlementResult;
import com.seashare.seasharem3.application.port.out.SettlementGatewayPort;
import com.seashare.seasharem3.domain.exception.GatewayBusinessRejectionException;
import com.seashare.seasharem3.domain.exception.GatewayCommunicationException;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.infrastructure.config.GatewayProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Adaptador de Mercado Pago para UC10. La dispersion se envia como un disbursement al cobro
 * original y el detalle se consulta por la referencia externa guardada en la intencion.
 */
public class MercadoPagoSettlementAdapter implements SettlementGatewayPort {

    private static final String ADVANCED_PAYMENTS_PATH = "/v1/advanced_payments";
    private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";
    private static final int BAD_REQUEST = 400;
    private static final String DEPENDENCY_NAME = "settlementGateway";

    private final RestClient restClient;
    private final GatewayProperties properties;
    private final JsonMapper mapper;
    private final CircuitBreaker circuitBreaker;

    public MercadoPagoSettlementAdapter(GatewayProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mapper = JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
        this.restClient = buildRestClient(properties);
        this.circuitBreaker = buildCircuitBreaker(properties.getCircuitBreaker());
    }

    @Override
    public GatewaySettlementResult send(GatewaySettlementRequest request) {
        Objects.requireNonNull(request, "request");
        MercadoPagoSettlementRequest body = new MercadoPagoSettlementRequest(List.of(
                new MercadoPagoSettlementRequest.Disbursement(request.amount().toOutputAmount(),
                        request.collectorId(), request.reservationId().toString())));
        try {
            Supplier<GatewaySettlementResult> call = () -> executeSend(body, request);
            return CircuitBreaker.decorateSupplier(circuitBreaker, call).get();
        } catch (CallNotPermittedException exception) {
            throw communicationFailure("The payment gateway circuit breaker is open", exception);
        }
    }

    private GatewaySettlementResult executeSend(MercadoPagoSettlementRequest body,
            GatewaySettlementRequest request) {
        try {
            String response = restClient.post()
                    .uri(ADVANCED_PAYMENTS_PATH + "/" + request.externalReference() + "/disbursements")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken())
                    .header(IDEMPOTENCY_HEADER, request.operationKey())
                    .body(mapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
            return toResult(mapper.readValue(response, MercadoPagoSettlementResponse.class));
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == BAD_REQUEST) {
                throw new GatewayBusinessRejectionException(
                        "The payment gateway rejected the settlement request: "
                                + exception.getResponseBodyAsString(),
                        exception);
            }
            throw communicationFailure("The payment gateway rejected the settlement request", exception);
        } catch (RestClientException exception) {
            throw communicationFailure("The payment gateway did not answer the settlement request", exception);
        }
    }

    @Override
    public Optional<GatewaySettlementResult> fetch(String externalReference) {
        Objects.requireNonNull(externalReference, "externalReference");
        try {
            Supplier<Optional<GatewaySettlementResult>> call = () -> executeFetch(externalReference);
            return CircuitBreaker.decorateSupplier(circuitBreaker, call).get();
        } catch (CallNotPermittedException exception) {
            throw communicationFailure("The payment gateway circuit breaker is open", exception);
        }
    }

    private Optional<GatewaySettlementResult> executeFetch(String externalReference) {
        try {
            String response = restClient.get()
                    .uri(ADVANCED_PAYMENTS_PATH + "/" + externalReference)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken())
                    .retrieve()
                    .body(String.class);
            return Optional.of(toResult(mapper.readValue(response, MercadoPagoSettlementResponse.class)));
        } catch (HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        } catch (RestClientResponseException exception) {
            throw communicationFailure("The payment gateway did not answer the settlement query", exception);
        } catch (RestClientException exception) {
            throw communicationFailure("The payment gateway did not answer the settlement query", exception);
        }
    }

    private static GatewaySettlementResult toResult(MercadoPagoSettlementResponse response) {
        return new GatewaySettlementResult(response.status(),
                response.id() == null ? null : String.valueOf(response.id()), response.statusDetail(),
                response.amount() == null ? null : new Money(response.amount()));
    }

    private String bearerToken() {
        return "Bearer " + properties.getAccessToken();
    }

    private static RestClient buildRestClient(GatewayProperties properties) {
        String baseUrl = Objects.requireNonNull(properties.getBaseUrl(), "baseUrl");
        if (baseUrl.isBlank()) {
            throw new IllegalArgumentException("seashare.gateway.base-url must be configured");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    private static CircuitBreaker buildCircuitBreaker(GatewayProperties.CircuitBreaker properties) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(properties.getFailureRateThreshold())
                .minimumNumberOfCalls(properties.getMinimumNumberOfCalls())
                .slidingWindowSize(properties.getSlidingWindowSize())
                .waitDurationInOpenState(properties.getWaitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(properties.getPermittedNumberOfCallsInHalfOpenState())
                .ignoreExceptions(GatewayBusinessRejectionException.class)
                .build();
        return CircuitBreaker.of(DEPENDENCY_NAME, config);
    }

    private static GatewayCommunicationException communicationFailure(String message, Throwable cause) {
        return new GatewayCommunicationException(message + ": " + cause.getMessage(), cause);
    }
}
