package com.seashare.seasharem3.contract;

import com.seashare.seasharem3.application.dto.BatchEstimateResult;
import com.seashare.seasharem3.application.port.in.EstimateBatchUseCase;
import com.seashare.seasharem3.application.port.in.EstimateSingleUseCase;
import com.seashare.seasharem3.infrastructure.adapter.in.web.EstimateController;
import com.seashare.seasharem3.infrastructure.config.CorsConfiguration;
import com.seashare.seasharem3.infrastructure.config.web.JacksonConfig;
import com.seashare.seasharem3.infrastructure.config.web.ProblemDetailsConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EstimateController.class,
        properties = "seashare.cors.allowed-origins=https://frontend.example.com")
@Import({CorsConfiguration.class, JacksonConfig.class, ProblemDetailsConfig.class})
class CorsContractTest {

    private static final String ENDPOINT = "/api/v1/estimates/batch";
    private static final String ORIGIN = "https://frontend.example.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EstimateBatchUseCase batchUseCase;

    @MockitoBean
    private EstimateSingleUseCase singleUseCase;

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT"})
    void allowsConfiguredOriginAndPreflightWithoutCredentials(String method) throws Exception {
        mockMvc.perform(options(ENDPOINT)
                        .header("Origin", ORIGIN)
                        .header("Access-Control-Request-Method", method)
                        .header("Access-Control-Request-Headers", "content-type,x-correlation-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Methods", "GET,POST,PUT,OPTIONS"))
                .andExpect(header().exists("Access-Control-Allow-Headers"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        verifyNoInteractions(batchUseCase, singleUseCase);
    }

    @Test
    void successfulRequestIncludesConfiguredOrigin() throws Exception {
        when(batchUseCase.estimate(any())).thenReturn(
                new BatchEstimateResult(LocalDate.of(2026, 11, 19), 1, 1, List.of(), List.of()));

        mockMvc.perform(post(ENDPOINT).header("Origin", ORIGIN)
                        .contentType("application/json").content("{\"boat_ids\":[]}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void actualRequestIncludesCorsHeadersEvenOnValidationError() throws Exception {
        mockMvc.perform(post(ENDPOINT).header("Origin", ORIGIN)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Expose-Headers",
                        "X-Correlation-Id, Content-Disposition"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void rejectsUnlistedOriginOnPreflightAndActualRequest() throws Exception {
        mockMvc.perform(options(ENDPOINT)
                        .header("Origin", "https://other.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mockMvc.perform(post(ENDPOINT).header("Origin", "http://localhost:5173")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        verifyNoInteractions(batchUseCase, singleUseCase);
    }

    @Test
    void rejectsMethodsNotEnabledForCors() throws Exception {
        mockMvc.perform(options(ENDPOINT).header("Origin", ORIGIN)
                        .header("Access-Control-Request-Method", "DELETE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestsWithoutOriginKeepTheirExistingBehavior() throws Exception {
        mockMvc.perform(post(ENDPOINT).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
