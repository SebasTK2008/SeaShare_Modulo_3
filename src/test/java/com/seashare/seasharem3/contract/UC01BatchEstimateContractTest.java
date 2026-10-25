package com.seashare.seasharem3.contract;

import com.seashare.seasharem3.application.dto.BatchEstimateResult;
import com.seashare.seasharem3.application.dto.BoatEstimate;
import com.seashare.seasharem3.application.port.in.EstimateBatchUseCase;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.infrastructure.adapter.in.web.EstimateController;
import com.seashare.seasharem3.infrastructure.adapter.in.web.error.ApiProblemDetailsFactory;
import com.seashare.seasharem3.infrastructure.adapter.in.web.error.GlobalExceptionHandler;
import com.seashare.seasharem3.infrastructure.config.web.JacksonConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.Collections;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UC01BatchEstimateContractTest {

    private static final String ENDPOINT = "/api/v1/estimates/batch";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        EstimateBatchUseCase batchUseCase = command -> new BatchEstimateResult(
                LocalDate.now(), 1, 1,
                Collections.singletonList(new BoatEstimate(
                        new BoatId(UUID.fromString("3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01")),
                        new com.seashare.seasharem3.domain.valueobject.Money(new java.math.BigDecimal("365000.00")))),
                Collections.emptyList());

        JsonMapper.Builder builder = JsonMapper.builder();
        new JacksonConfig().domainScalarsCustomizer().customize(builder);
        builder.configure(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        builder.addMixIn(org.springframework.http.ProblemDetail.class,
                org.springframework.http.converter.json.ProblemDetailJacksonMixin.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new EstimateController(batchUseCase, cmd -> null))
                .setControllerAdvice(new GlobalExceptionHandler(new ApiProblemDetailsFactory()))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(builder.build()))
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    @Test
    void batchValidReturns200WithVersionTrace() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boat_ids\":[\"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluation_date").exists())
                .andExpect(jsonPath("$.duration_days").value(1))
                .andExpect(jsonPath("$.estimates[0].boat_id").exists())
                .andExpect(jsonPath("$.estimates[0].estimated_total").exists());
    }

    @Test
    void batchEmptyReturns200WithEmptyArrays() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boat_ids\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimates").isArray())
                .andExpect(jsonPath("$.unavailable").isArray());
    }

    @Test
    void batchExtraFieldsReturns400ValidationError() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boat_ids\":[],\"start_date\":\"2026-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
