package com.seashare.seasharem3.contract;

import com.seashare.seasharem3.application.dto.SingleEstimateResult;
import com.seashare.seasharem3.application.port.in.EstimateSingleUseCase;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UC01SingleEstimateContractTest {

    private static final String ENDPOINT = "/api/v1/estimates/individual";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        EstimateSingleUseCase singleUseCase = command -> new SingleEstimateResult(
                new BoatId(UUID.fromString("3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01")),
                command.startDate(),
                command.endDate(),
                3,
                command.passengers(),
                new com.seashare.seasharem3.domain.valueobject.Money(new BigDecimal("365000.00")),
                "Valor estimado. El valor incluye el seguro náutico, pero no incluye el depósito de garantía ni penalidades o ajustes derivados de cambios posteriores de la reserva");

        JsonMapper.Builder builder = JsonMapper.builder();
        new JacksonConfig().domainScalarsCustomizer().customize(builder);
        builder.configure(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        builder.addMixIn(org.springframework.http.ProblemDetail.class,
                org.springframework.http.converter.json.ProblemDetailJacksonMixin.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new EstimateController(
                        (cmd) -> null, singleUseCase))
                .setControllerAdvice(new GlobalExceptionHandler(new ApiProblemDetailsFactory()))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(builder.build()))
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    @Test
    void individualValidReturns200WithWarning() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boat_id\":\"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01\",\"start_date\":\"2026-01-01\",\"end_date\":\"2026-01-03\",\"passengers\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.boat_id").exists())
                .andExpect(jsonPath("$.duration_days").value(3))
                .andExpect(jsonPath("$.estimated_total").exists())
                .andExpect(jsonPath("$.warning").exists());
    }

    @Test
    void individualInvalidDatesReturns400() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boat_id\":\"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01\",\"start_date\":\"2026-01-05\",\"end_date\":\"2026-01-01\",\"passengers\":1}"))
                .andExpect(status().isBadRequest());
    }
}
