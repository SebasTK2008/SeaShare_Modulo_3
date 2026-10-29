package com.seashare.seasharem3.contract;

import com.seashare.seasharem3.application.dto.CalculateReservationValueCommand;
import com.seashare.seasharem3.application.dto.ReservationValueResult;
import com.seashare.seasharem3.application.port.in.CalculateReservationValueUseCase;
import com.seashare.seasharem3.domain.exception.FinancialParametersNotConfiguredException;
import com.seashare.seasharem3.domain.exception.ReservationInfoIncompleteException;
import com.seashare.seasharem3.domain.exception.ReservationInfoNotFoundException;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.infrastructure.adapter.in.web.CorrelationIdFilter;
import com.seashare.seasharem3.infrastructure.adapter.in.web.ReservationValueController;
import com.seashare.seasharem3.infrastructure.adapter.in.web.error.ApiProblemDetailsFactory;
import com.seashare.seasharem3.infrastructure.adapter.in.web.error.GlobalExceptionHandler;
import com.seashare.seasharem3.infrastructure.config.web.JacksonConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de contrato del endpoint de UC04 (T013, T017): flujo feliz, {@code X-Correlation-Id},
 * identificador inválido (E2 antes de E3) y los Problem Details de 404/422/503/500.
 * Fuente: {@code contracts/rest/UC04-valor-calculado-reserva.md} §§2–5 y SPEC 04.
 */
class UC04CalculatedValueContractTest {

    private static final UUID RESERVATION_UUID = UUID.fromString("b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10");
    private static final String ENDPOINT = "/api/v1/reservations/" + RESERVATION_UUID + "/calculated-value";

    private MockMvc mockMvc;
    private StubCalculateReservationValueUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new StubCalculateReservationValueUseCase();

        JsonMapper.Builder builder = JsonMapper.builder();
        new JacksonConfig().domainScalarsCustomizer().customize(builder);
        builder.addMixIn(org.springframework.http.ProblemDetail.class,
                org.springframework.http.converter.json.ProblemDetailJacksonMixin.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new ReservationValueController(useCase))
                .setControllerAdvice(new GlobalExceptionHandler(new ApiProblemDetailsFactory()))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(builder.build()))
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void calculateReturns200WithTheContractBreakdown() throws Exception {
        // T013: el cuerpo del contrato §4, con montos como string decimal de 2 decimales.
        useCase.result = new ReservationValueResult(new ReservationId(RESERVATION_UUID),
                new Money(new BigDecimal("900000.00")), new Money(new BigDecimal("60000.00")),
                new Money(new BigDecimal("30000.00")), new Money(new BigDecimal("990000.00")));

        mockMvc.perform(post(ENDPOINT).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.reservation_id").value(RESERVATION_UUID.toString()))
                .andExpect(jsonPath("$.rental_amount").value("900000.00"))
                .andExpect(jsonPath("$.insurance_amount").value("60000.00"))
                .andExpect(jsonPath("$.guarantee_deposit_amount").value("30000.00"))
                .andExpect(jsonPath("$.total_amount").value("990000.00"));

        assertThat(useCase.command).isEqualTo(new CalculateReservationValueCommand(new ReservationId(RESERVATION_UUID)));
    }

    @Test
    void calculateEchoesTheProvidedCorrelationId() throws Exception {
        useCase.result = resultOfZeros();

        mockMvc.perform(post(ENDPOINT).header(CorrelationIdFilter.HEADER, "corr-uc04"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER, "corr-uc04"));
    }

    @Test
    void calculateGeneratesACorrelationIdWhenTheClientDoesNotSendOne() throws Exception {
        useCase.result = resultOfZeros();

        mockMvc.perform(post(ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER, not(blankOrNullString())));
    }

    @Test
    void invalidReservationIdReturns400WithoutInvokingTheUseCase() throws Exception {
        // Orden de validación: una petición mal formada (E2) nunca llega a la base de datos (E3).
        mockMvc.perform(post("/api/v1/reservations/not-a-uuid/calculated-value"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.retryable").value(false));

        assertThat(useCase.command).isNull();
    }

    @Test
    void unknownReservationReturns404WithRetryableProblemDetails() throws Exception {
        useCase.exceptionToThrow = new ReservationInfoNotFoundException(
                "There is no registered information for reservation " + RESERVATION_UUID + ".");

        mockMvc.perform(post(ENDPOINT).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Reservation information not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("RESERVATION_INFO_NOT_FOUND"))
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(jsonPath("$.detail")
                        .value("There is no registered information for reservation " + RESERVATION_UUID + "."));
    }

    @Test
    void incompleteInformationReturns422ProblemDetails() throws Exception {
        useCase.exceptionToThrow = new ReservationInfoIncompleteException(
                "The registered information for reservation " + RESERVATION_UUID
                        + " is incomplete: base_rate is required to calculate the value.");

        mockMvc.perform(post(ENDPOINT))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Reservation information incomplete"))
                .andExpect(jsonPath("$.code").value("RESERVATION_INFO_INCOMPLETE"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void missingParametersReturn503ProblemDetails() throws Exception {
        useCase.exceptionToThrow = new FinancialParametersNotConfiguredException(
                "There is no financial parameters version available for reservation " + RESERVATION_UUID + ".");

        mockMvc.perform(post(ENDPOINT))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Financial parameters not configured"))
                .andExpect(jsonPath("$.code").value("FINANCIAL_PARAMETERS_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    void unexpectedFailureReturns500WithoutLeakingTechnicalDetails() throws Exception {
        useCase.exceptionToThrow = new IllegalStateException("secret connection string");

        mockMvc.perform(post(ENDPOINT))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Internal error"))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(jsonPath("$.detail").value("An unexpected internal error occurred."));
    }

    private static ReservationValueResult resultOfZeros() {
        Money zero = new Money(BigDecimal.ZERO);
        return new ReservationValueResult(new ReservationId(RESERVATION_UUID), zero, zero, zero, zero);
    }

    private static final class StubCalculateReservationValueUseCase implements CalculateReservationValueUseCase {

        ReservationValueResult result;
        RuntimeException exceptionToThrow;
        CalculateReservationValueCommand command;

        @Override
        public ReservationValueResult calculate(CalculateReservationValueCommand command) {
            this.command = command;
            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }
            return result;
        }
    }
}
