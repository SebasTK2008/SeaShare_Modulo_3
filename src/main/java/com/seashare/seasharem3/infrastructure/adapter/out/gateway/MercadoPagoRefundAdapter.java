package com.seashare.seasharem3.infrastructure.adapter.out.gateway;

import com.seashare.seasharem3.application.dto.RefundGatewayRequest;
import com.seashare.seasharem3.application.dto.RefundGatewayResult;
import com.seashare.seasharem3.application.port.out.RefundGatewayPort;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Adaptador de Mercado Pago para UC09. Envia reembolsos totales o parciales con la misma clave
 * idempotente y consulta el detalle dentro del pago original.
 */
public class MercadoPagoRefundAdapter implements RefundGatewayPort {

    private static final String PAYMENTS_PATH = "/v1/payments";
    private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";
    private static final int BAD_REQUEST = 400;
    private static final String DEPENDENCY_NAME = "refundGateway";

    private final RestClient restClient;
    private final GatewayProperties properties;
    private final JsonMapper mapper;
    private final CircuitBreaker circuitBreaker;

    public MercadoPagoRefundAdapter(GatewayProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mapper = JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
        this.restClient = buildRestClient(properties);
        this.circuitBreaker = buildCircuitBreaker(properties.getCircuitBreaker());
    }

    @Override
    public RefundGatewayResult send(RefundGatewayRequest request) {
        Objects.requireNonNull(request, "request");
        MercadoPagoRefundRequest body = new MercadoPagoRefundRequest(request.amount().toOutputAmount());
        try {
            Supplier<RefundGatewayResult> call = () -> executeSend(body, request);
            return CircuitBreaker.decorateSupplier(circuitBreaker, call).get();
        } catch (CallNotPermittedException exception) {
            throw communicationFailure("The payment gateway circuit breaker is open", exception);
        }
    }

    private RefundGatewayResult executeSend(MercadoPagoRefundRequest body, RefundGatewayRequest request) {
        try {
            String response = restClient.post()
                    .uri(PAYMENTS_PATH + "/" + request.originalChargeReference() + "/refunds")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken())
                    .header(IDEMPOTENCY_HEADER, request.operationKey())
                    .body(mapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
            return toResult(mapper.readValue(response, MercadoPagoRefundResponse.class));
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == BAD_REQUEST) {
                throw new GatewayBusinessRejectionException(
                        "The payment gateway rejected the refund request: " + exception.getResponseBodyAsString(),
                        exception);
            }
            throw communicationFailure("The payment gateway rejected the refund request", exception);
        } catch (RestClientException exception) {
            throw communicationFailure("The payment gateway did not answer the refund request", exception);
        }
    }

    @Override
    public Optional<RefundGatewayResult> fetchRefund(String chargeReference, String refundReference) {
        Objects.requireNonNull(chargeReference, "chargeReference");
        Objects.requireNonNull(refundReference, "refundReference");
        try {
            Supplier<Optional<RefundGatewayResult>> call = () -> executeFetch(chargeReference, refundReference);
            return CircuitBreaker.decorateSupplier(circuitBreaker, call).get();
        } catch (CallNotPermittedException exception) {
            throw communicationFailure("The payment gateway circuit breaker is open", exception);
        }
    }

    private Optional<RefundGatewayResult> executeFetch(String chargeReference, String refundReference) {
        try {
            String response = restClient.get()
                    .uri(PAYMENTS_PATH + "/" + chargeReference + "/refunds/" + refundReference)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken())
                    .retrieve()
                    .body(String.class);
            return Optional.of(toResult(mapper.readValue(response, MercadoPagoRefundResponse.class)));
        } catch (HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        } catch (RestClientResponseException exception) {
            throw communicationFailure("The payment gateway did not answer the refund query", exception);
        } catch (RestClientException exception) {
            throw communicationFailure("The payment gateway did not answer the refund query", exception);
        }
    }

    private static RefundGatewayResult toResult(MercadoPagoRefundResponse response) {
        return new RefundGatewayResult(response.status(),
                response.id() == null ? null : String.valueOf(response.id()),
                response.amount() == null ? null : new Money(response.amount()), response.statusDetail(),
                transactionCost(response.feeDetails()));
    }

    private static Money transactionCost(List<MercadoPagoRefundResponse.FeeDetail> fees) {
        if (fees == null || fees.isEmpty()) {
            return null;
        }
        BigDecimal total = fees.stream().map(MercadoPagoRefundResponse.FeeDetail::amount)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Money(total);
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
