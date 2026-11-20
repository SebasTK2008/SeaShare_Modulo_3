package com.seashare.seasharem3.contract;

import com.seashare.seasharem3.application.port.in.EstimateBatchUseCase;
import com.seashare.seasharem3.application.port.in.EstimateSingleUseCase;
import com.seashare.seasharem3.infrastructure.adapter.in.web.EstimateController;
import com.seashare.seasharem3.infrastructure.config.CorsConfiguration;
import com.seashare.seasharem3.infrastructure.config.web.ProblemDetailsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EstimateController.class, properties = "seashare.cors.allowed-origins=")
@Import({CorsConfiguration.class, ProblemDetailsConfig.class})
class CorsDisabledContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EstimateBatchUseCase batchUseCase;

    @MockitoBean
    private EstimateSingleUseCase singleUseCase;

    @Test
    void emptyConfigurationDoesNotAuthorizeAnyOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/estimates/batch")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        verifyNoInteractions(batchUseCase, singleUseCase);
    }
}
