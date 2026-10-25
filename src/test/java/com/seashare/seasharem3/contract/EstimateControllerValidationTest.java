package com.seashare.seasharem3.contract;

import com.seashare.seasharem3.application.dto.BatchEstimateResult;
import com.seashare.seasharem3.application.port.in.EstimateBatchUseCase;
import com.seashare.seasharem3.infrastructure.adapter.in.web.EstimateController;
import com.seashare.seasharem3.infrastructure.adapter.in.web.error.ApiProblemDetailsFactory;
import com.seashare.seasharem3.infrastructure.adapter.in.web.error.GlobalExceptionHandler;

import com.seashare.seasharem3.infrastructure.config.web.JacksonConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.Collections;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EstimateControllerValidationTest {

    private static final String ENDPOINT = "/api/v1/estimates/batch";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        EstimateBatchUseCase batchUseCase = command -> new BatchEstimateResult(
                LocalDate.of(2026, 9, 30), 1, 1,
                Collections.emptyList(), Collections.emptyList());

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
    void batchExtraFieldsReturns400ValidationError() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boat_ids\":[\"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01\"],\"start_date\":\"2026-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void batchExceedsMaxSizeReturns400BatchSizeExceeded() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boat_ids\":[\"id01\",\"id02\",\"id03\",\"id04\",\"id05\",\"id06\",\"id07\",\"id08\",\"id09\",\"id10\",\"id11\",\"id12\",\"id13\",\"id14\",\"id15\",\"id16\",\"id17\",\"id18\",\"id19\",\"id20\",\"id21\",\"id22\",\"id23\",\"id24\",\"id25\",\"id26\",\"id27\",\"id28\",\"id29\",\"id30\",\"id31\",\"id32\",\"id33\",\"id34\",\"id35\",\"id36\",\"id37\",\"id38\",\"id39\",\"id40\",\"id41\",\"id42\",\"id43\",\"id44\",\"id45\",\"id46\",\"id47\",\"id48\",\"id49\",\"id50\",\"id51\"]}"))
                .andExpect(status().isBadRequest());
    }
}
