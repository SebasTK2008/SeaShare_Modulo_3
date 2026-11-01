package com.seashare.seasharem3.infrastructure.adapter.out.gateway;

import com.seashare.seasharem3.application.dto.ChargeGatewayRequest;
import com.seashare.seasharem3.application.dto.ChargeGatewayResult;
import com.seashare.seasharem3.application.port.out.ChargeGatewayPort;
import com.seashare.seasharem3.domain.exception.GatewayCommunicationException;
import com.seashare.seasharem3.domain.exception.InvalidPaymentTokenException;
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
import tools.jackson.core.JacksonException;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Adaptador de la pasarela de pago (Mercado Pago) para UC05: envio del cobro y consulta del pago.
 *
 * <p>El envio es un {@code POST /v1/payments} con la cabecera {@code X-Idempotency-Key} del
 * intento, de modo que un reintento técnico no genere un cobro doble (SPEC RNF-003; general-plan
 * D-33). El cuerpo se serializa con un {@code JsonMapper} propio: el de la API escribe los
 * decimales como cadena y Mercado Pago exige un numero (contracts/README §3.1).</p>
 *
 * <p>Los errores se clasifican segun el contrato §5: un {@code 400} de validacion del token no es
 * reintentable y se traduce a {@link InvalidPaymentTokenException}; un timeout, un {@code 502} o un
 * {@code 503} (y cualquier otra falla de la pasarela) se traducen a
 * {@link GatewayCommunicationException}, que deja el resultado del cobro desconocido y permite
 * reintentar con la misma clave.</p>
 *
 * <p>Fuente: UC05 plan T012 y T021; contrato {@code pasarela-comando-cobro.md} §2, §4 y §5.</p>
 */
public class MercadoPagoChargeAdapter implements ChargeGatewayPort {

    private static final String PAYMENTS_PATH = "/v1/payments";
    private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";
    private static final String CREDIT_CARD = "CREDIT_CARD";
    private static final String MERCADO_PAGO_CREDIT_CARD = "credit_card";
    private static final int BAD_REQUEST = 400;
    private static final String DEPENDENCY_NAME = "paymentGateway";

    private final RestClient restClient;
    private final GatewayProperties properties;
    private final JsonMapper mapper;
    private final CircuitBreaker circuitBreaker;

    public MercadoPagoChargeAdapter(GatewayProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        this.restClient = buildRestClient(properties);
        this.circuitBreaker = buildCircuitBreaker(properties.getCircuitBreaker());
    }

    @Override
    public ChargeGatewayResult send(ChargeGatewayRequest request) {
        Objects.requireNonNull(request, "request");
        MercadoPagoPaymentRequest body = new MercadoPagoPaymentRequest(
                request.transactionAmount().toOutputAmount(),
                request.token(),
                request.description(),
                request.installments(),
                paymentMethodId(request.paymentMethodId()),
                new MercadoPagoPaymentRequest.Payer(request.payerEmail()),
                request.externalReference());

        try {
            Supplier<ChargeGatewayResult> call = () -> executeSend(body, request.operationKey());
            return CircuitBreaker.decorateSupplier(circuitBreaker, call).get();
        } catch (CallNotPermittedException exception) {
            throw communicationFailure("The payment gateway circuit breaker is open", exception);
        }
    }

    private ChargeGatewayResult executeSend(MercadoPagoPaymentRequest body, String operationKey) {
        try {
            String response = restClient.post()
                    .uri(PAYMENTS_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken())
                    .header(IDEMPOTENCY_HEADER, operationKey)
                    .body(mapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
            return toResult(mapper.readValue(response, MercadoPagoPaymentResponse.class));
        } catch (JacksonException exception) {
            throw communicationFailure("The payment gateway returned an invalid charge response", exception);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == BAD_REQUEST) {
                throw new InvalidPaymentTokenException(
                        "The payment gateway rejected the charge request: " + exception.getResponseBodyAsString(),
                        exception);
            }
            throw communicationFailure("The payment gateway rejected the charge request", exception);
        } catch (RestClientException exception) {
            throw communicationFailure("The payment gateway did not answer the charge request", exception);
        }
    }

    @Override
    public Optional<ChargeGatewayResult> fetchPayment(String externalReference) {
        Objects.requireNonNull(externalReference, "externalReference");
        try {
            Supplier<Optional<ChargeGatewayResult>> call = () -> executeFetch(externalReference);
            return CircuitBreaker.decorateSupplier(circuitBreaker, call).get();
        } catch (CallNotPermittedException exception) {
            throw communicationFailure("The payment gateway circuit breaker is open", exception);
        }
    }

    private Optional<ChargeGatewayResult> executeFetch(String externalReference) {
        try {
            String response = restClient.get()
                    .uri(PAYMENTS_PATH + "/" + externalReference)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken())
                    .retrieve()
                    .body(String.class);
            return Optional.of(toResult(mapper.readValue(response, MercadoPagoPaymentResponse.class)));
        } catch (JacksonException exception) {
            throw communicationFailure("The payment gateway returned an invalid payment response", exception);
        } catch (HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        } catch (RestClientResponseException exception) {
            throw communicationFailure("The payment gateway did not answer the payment query", exception);
        } catch (RestClientException exception) {
            throw communicationFailure("The payment gateway did not answer the payment query", exception);
        }
    }

    /**
     * Traduccion del tipo de medio: Mercado Pago exige {@code credit_card} y el mensaje de UC07
     * transporta {@code CREDIT_CARD}; cualquier otro valor pasa tal cual.
     */
    private static String paymentMethodId(String paymentMethodType) {
        if (paymentMethodType == null) {
            return null;
        }
        return CREDIT_CARD.equals(paymentMethodType) ? MERCADO_PAGO_CREDIT_CARD : paymentMethodType;
    }

    private String bearerToken() {
        return "Bearer " + properties.getAccessToken();
    }

    private static ChargeGatewayResult toResult(MercadoPagoPaymentResponse response) {
        return new ChargeGatewayResult(response.status(),
                response.id() == null ? null : String.valueOf(response.id()), response.statusDetail());
    }

    private static RestClient buildRestClient(GatewayProperties properties) {
        String baseUrl = Objects.requireNonNull(properties.getBaseUrl(), "baseUrl");
        if (baseUrl.isBlank()) {
            throw new IllegalArgumentException("seashare.gateway.base-url must be configured");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    private static CircuitBreaker buildCircuitBreaker(GatewayProperties.CircuitBreaker properties) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(properties.getFailureRateThreshold())
                .minimumNumberOfCalls(properties.getMinimumNumberOfCalls())
                .slidingWindowSize(properties.getSlidingWindowSize())
                .waitDurationInOpenState(properties.getWaitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(properties.getPermittedNumberOfCallsInHalfOpenState())
                // Un 400 de validacion del token no es una caida de la pasarela: no cuenta como fallo
                // del circuito, para no abrirlo contra trafico valido (contrato §5).
                .ignoreExceptions(InvalidPaymentTokenException.class)
                .build();
        return CircuitBreaker.of(DEPENDENCY_NAME, config);
    }

    private static GatewayCommunicationException communicationFailure(String message, Throwable cause) {
        return new GatewayCommunicationException(message + ": " + cause.getMessage(), cause);
    }
}
