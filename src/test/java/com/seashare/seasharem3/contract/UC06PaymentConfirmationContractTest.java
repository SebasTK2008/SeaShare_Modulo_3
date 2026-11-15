package com.seashare.seasharem3.contract;

import com.seashare.seasharem3.application.dto.PaymentConfirmationQuery;
import com.seashare.seasharem3.application.dto.PaymentConfirmationResult;
import com.seashare.seasharem3.application.port.in.GetPaymentConfirmationUseCase;
import com.seashare.seasharem3.domain.exception.ChargeIntentNotFoundException;
import com.seashare.seasharem3.domain.valueobject.PaymentConfirmationStatus;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.infrastructure.adapter.in.web.CorrelationIdFilter;
import com.seashare.seasharem3.infrastructure.adapter.in.web.PaymentConfirmationController;
import com.seashare.seasharem3.infrastructure.adapter.in.web.error.ApiProblemDetailsFactory;
import com.seashare.seasharem3.infrastructure.adapter.in.web.error.GlobalExceptionHandler;
import com.seashare.seasharem3.infrastructure.config.web.JacksonConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de contrato del endpoint de UC06: cuerpo de 200 por estado, montos disponibles, 400 por
 * UUID inválido, 404 sin intención y 500 no previsto. Fuente:
 * {@code contracts/rest/UC06-confirmacion-pago.md} §§2–5 y SPEC 06.
 */
class UC06PaymentConfirmationContractTest {

    private static final UUID RESERVATION_UUID = UUID.fromString("b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10");
    private static final ReservationId RESERVATION_ID = new ReservationId(RESERVATION_UUID);
    private static final String ENDPOINT = "/api/v1/reservations/" + RESERVATION_UUID + "/payment-confirmation";

    private MockMvc mockMvc;
    private StubGetPaymentConfirmationUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new StubGetPaymentConfirmationUseCase();

        JsonMapper.Builder builder = JsonMapper.builder();
        new JacksonConfig().domainScalarsCustomizer().customize(builder);
        builder.addMixIn(org.springframework.http.ProblemDetail.class,
                org.springframework.http.converter.json.ProblemDetailJacksonMixin.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new PaymentConfirmationController(useCase))
                .setControllerAdvice(new GlobalExceptionHandler(new ApiProblemDetailsFactory()))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(builder.build()))
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void returns200WithTheContractFieldsForApproved() throws Exception {
        useCase.result = new PaymentConfirmationResult(RESERVATION_ID, PaymentConfirmationStatus.APPROVED,
                "accredited", null, null, null, new BigDecimal("990000.00"), "pg-8841");

        mockMvc.perform(get(ENDPOINT).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.reservation_id").value(RESERVATION_UUID.toString()))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.detail").value("accredited"))
                .andExpect(jsonPath("$.authorized_amount").value(nullValue()))
                .andExpect(jsonPath("$.captured_amount").value(nullValue()))
                .andExpect(jsonPath("$.released_amount").value(nullValue()))
                .andExpect(jsonPath("$.charged_amount").value("990000.00"))
                .andExpect(jsonPath("$.external_reference").value("pg-8841"));

        assertThat(useCase.query).isEqualTo(new PaymentConfirmationQuery(RESERVATION_ID));
    }

    @ParameterizedTest
    @EnumSource(PaymentConfirmationStatus.class)
    void returns200WithTheFullContractBodyForEveryStatus(PaymentConfirmationStatus status) throws Exception {
        String detail = status == PaymentConfirmationStatus.UNKNOWN ? null : "detail-" + status;
        BigDecimal chargedAmount = status == PaymentConfirmationStatus.APPROVED
                ? new BigDecimal("990000.00")
                : null;
        useCase.result = new PaymentConfirmationResult(RESERVATION_ID, status, detail, null, null, null,
                chargedAmount, "pg-8841");

        ResultActions response = mockMvc.perform(get(ENDPOINT).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.reservation_id").value(RESERVATION_UUID.toString()))
                .andExpect(jsonPath("$.status").value(status.name()))
                .andExpect(jsonPath("$.detail").hasJsonPath())
                .andExpect(jsonPath("$.authorized_amount").hasJsonPath())
                .andExpect(jsonPath("$.authorized_amount").value(nullValue()))
                .andExpect(jsonPath("$.captured_amount").hasJsonPath())
                .andExpect(jsonPath("$.captured_amount").value(nullValue()))
                .andExpect(jsonPath("$.released_amount").hasJsonPath())
                .andExpect(jsonPath("$.released_amount").value(nullValue()))
                .andExpect(jsonPath("$.charged_amount").hasJsonPath())
                .andExpect(jsonPath("$.external_reference").value("pg-8841"));

        if (detail == null) {
            response.andExpect(jsonPath("$.detail").value(nullValue()));
        } else {
            response.andExpect(jsonPath("$.detail").value(detail));
        }
        if (chargedAmount == null) {
            response.andExpect(jsonPath("$.charged_amount").value(nullValue()));
        } else {
            response.andExpect(jsonPath("$.charged_amount").value("990000.00"));
        }
    }

    @Test
    void returns200ForInProcessWithoutAmounts() throws Exception {
        useCase.result = new PaymentConfirmationResult(RESERVATION_ID, PaymentConfirmationStatus.IN_PROCESS,
                "pending", null, null, null, null, null);

        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROCESS"))
                .andExpect(jsonPath("$.detail").value("pending"))
                .andExpect(jsonPath("$.charged_amount").value(nullValue()));
    }

    @Test
    void returns200ForRejectedWithItsDetail() throws Exception {
        useCase.result = new PaymentConfirmationResult(RESERVATION_ID, PaymentConfirmationStatus.REJECTED,
                "cc_rejected", null, null, null, null, "pg-1");

        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.detail").value("cc_rejected"))
                .andExpect(jsonPath("$.charged_amount").value(nullValue()));
    }

    @Test
    void echoesTheProvidedCorrelationId() throws Exception {
        useCase.result = new PaymentConfirmationResult(RESERVATION_ID, PaymentConfirmationStatus.EXPIRED,
                null, null, null, null, null, null);

        mockMvc.perform(get(ENDPOINT).header(CorrelationIdFilter.HEADER, "corr-uc06"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER, "corr-uc06"));
    }

    @Test
    void generatesACorrelationIdWhenTheClientDoesNotSendOne() throws Exception {
        useCase.result = new PaymentConfirmationResult(RESERVATION_ID, PaymentConfirmationStatus.CANCELLED,
                null, null, null, null, null, null);

        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER, not(blankOrNullString())));
    }

    @Test
    void invalidReservationIdReturns400WithoutInvokingTheUseCase() throws Exception {
        mockMvc.perform(get("/api/v1/reservations/not-a-uuid/payment-confirmation"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.retryable").value(false));

        assertThat(useCase.query).isNull();
    }

    @Test
    void missingIntentReturns404WithRetryableProblemDetails() throws Exception {
        useCase.exceptionToThrow = new ChargeIntentNotFoundException(
                "There is no ChargeIntent for reservation " + RESERVATION_UUID + ".");

        mockMvc.perform(get(ENDPOINT).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Charge intent not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("CHARGE_INTENT_NOT_FOUND"))
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(jsonPath("$.detail")
                        .value("There is no ChargeIntent for reservation " + RESERVATION_UUID + "."));
    }

    @Test
    void unexpectedFailureReturns500WithoutLeakingTechnicalDetails() throws Exception {
        useCase.exceptionToThrow = new IllegalStateException("secret connection string");

        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Internal error"))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(jsonPath("$.detail").value("An unexpected internal error occurred."));
    }

    private static final class StubGetPaymentConfirmationUseCase implements GetPaymentConfirmationUseCase {

        PaymentConfirmationResult result;
        RuntimeException exceptionToThrow;
        PaymentConfirmationQuery query;

        @Override
        public PaymentConfirmationResult get(PaymentConfirmationQuery query) {
            this.query = query;
            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }
            return result;
        }
    }
}
