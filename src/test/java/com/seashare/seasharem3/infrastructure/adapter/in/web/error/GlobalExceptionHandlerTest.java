package com.seashare.seasharem3.infrastructure.adapter.in.web.error;

import com.seashare.seasharem3.application.exception.ContractException;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.domain.exception.BaseRateException;
import com.seashare.seasharem3.domain.exception.ChargeIntentNotFoundException;
import com.seashare.seasharem3.domain.exception.FinancialParametersValidationException;
import com.seashare.seasharem3.domain.exception.InvalidPageException;
import com.seashare.seasharem3.domain.exception.ReservationInfoIncompleteException;
import com.seashare.seasharem3.domain.exception.ReservationInfoNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler(new ApiProblemDetailsFactory()))
                .build();
    }

    @Test
    void contractExceptionProducesCatalogProblemDetails() throws Exception {
        mockMvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"contract\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Invalid period"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_PERIOD"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void bodyValidationReturnsValidationErrorWithFields() throws Exception {
        mockMvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("value"));
    }

    @Test
    void unparseableBodyReturnsValidationError() throws Exception {
        mockMvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void unsupportedMethodReturnsMethodNotAllowed() throws Exception {
        mockMvc.perform(get("/probe"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unsupportedContentTypeReturnsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/probe").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void invalidPathVariableReturnsValidationError() throws Exception {
        mockMvc.perform(get("/probe/type/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.detail").value("The value of 'id' is not valid for UUID."));
    }

    @Test
    void invalidQueryParameterReturnsValidationError() throws Exception {
        mockMvc.perform(get("/probe/type").param("mode", "NOPE").param("count", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.detail").value("The value of 'mode' is not valid for ProbeMode."));
    }

    @Test
    void missingRequiredParameterReturnsValidationError() throws Exception {
        mockMvc.perform(get("/probe/type").param("mode", "FAST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.detail").value("The required parameter 'count' is missing."));
    }

    @Test
    void bindingErrorsReturnValidationErrorWithFields() throws Exception {
        mockMvc.perform(get("/probe/binding"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("value"));
    }

    @Test
    void reservationInfoNotFoundIsMappedToRetryableNotFound() throws Exception {
        mockMvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"reservation-not-found\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_INFO_NOT_FOUND"))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    void reservationInfoIncompleteIsMappedToUnprocessableEntity() throws Exception {
        mockMvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"reservation-incomplete\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RESERVATION_INFO_INCOMPLETE"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void chargeIntentNotFoundIsMappedToRetryableNotFound() throws Exception {
        mockMvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"charge-not-found\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHARGE_INTENT_NOT_FOUND"))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    void invalidPageIsMappedToPageOutOfRange() throws Exception {
        mockMvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"invalid-page\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAGE_OUT_OF_RANGE"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void financialParametersValidationReturnsFieldError() throws Exception {
        mockMvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"negative-insurance\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.errors[0].field").value("insurance_fee_per_passenger"));
    }

    @Test
    void unreadableFinancialParametersReturnInternalError() throws Exception {
        mockMvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"parameters-unavailable\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    void unhandledErrorReturnsInternalErrorWithoutLeakingTechnicalDetail() throws Exception {
        mockMvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"boom\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(jsonPath("$.detail").value("An unexpected internal error occurred."));
    }

    @Test
    void frameworkErrorResponseWithoutCatalogCodeIsEnrichedInFallback() {
        ProblemDetail problem = new GlobalExceptionHandler(new ApiProblemDetailsFactory())
                .handleUnexpected(new AsyncRequestTimeoutException());

        assertThat(problem.getStatus()).isEqualTo(503);
        assertThat(problem.getProperties()).containsEntry("code", "INTERNAL_ERROR");
        assertThat(problem.getProperties()).containsEntry("retryable", true);
    }

    @RequestMapping("/probe")
    @RestController
    static class ProbeController {

        @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
        void probe(@Valid @RequestBody ProbeBody body) {
            switch (body.value()) {
                case "contract" -> throw new ContractException(ErrorCode.INVALID_PERIOD, "The period is invalid.");
                case "reservation-not-found" ->
                        throw new ReservationInfoNotFoundException("Reservation information not found.");
                case "reservation-incomplete" ->
                        throw new ReservationInfoIncompleteException("Reservation information is incomplete.");
                case "charge-not-found" -> throw new ChargeIntentNotFoundException("Charge intent not found.");
                case "invalid-page" -> throw new InvalidPageException("Page out of range.");
                case "negative-insurance" -> throw new FinancialParametersValidationException(
                        "insuranceFeePerPassenger",
                        "The insurance fee per passenger must be greater than or equal to 0: -1.0000");
                case "parameters-unavailable" -> throw new BaseRateException(
                        BaseRateException.BaseRateFailureReason.PARAMETERS_UNAVAILABLE,
                        "Financial parameters could not be read.");
                case "boom" -> throw new IllegalStateException("secret trace that must not leak");
                default -> {
                }
            }
        }

        @GetMapping("/type/{id}")
        void pathVariable(@PathVariable UUID id) {
        }

        @GetMapping("/type")
        void queryParameter(@RequestParam("mode") ProbeMode mode, @RequestParam("count") int count) {
        }

        @GetMapping("/binding")
        void binding() throws BindException {
            BeanPropertyBindingResult result = new BeanPropertyBindingResult(new ProbeBody(""), "probeBody");
            result.rejectValue("value", "NotBlank", "must not be blank");
            throw new BindException(result);
        }
    }

    enum ProbeMode {
        FAST,
        SLOW
    }

    record ProbeBody(@NotBlank String value) {
    }
}
