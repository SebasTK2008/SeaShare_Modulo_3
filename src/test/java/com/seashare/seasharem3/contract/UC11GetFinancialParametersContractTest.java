package com.seashare.seasharem3.contract;

import com.seashare.seasharem3.application.dto.FinancialParametersResult;
import com.seashare.seasharem3.application.exception.ContractException;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.LoadFinancialParametersUseCase;
import com.seashare.seasharem3.application.port.in.SaveFinancialParametersUseCase;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import com.seashare.seasharem3.infrastructure.adapter.in.web.FinancialParametersController;
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
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de contrato del endpoint {@code GET /api/v1/financial-parameters} (T026, T027).
 * Fuente: {@code contracts/rest/UC11-obtener-parametros-financieros.md} y SPEC 11 HU3.
 */
class UC11GetFinancialParametersContractTest {

    private static final String ENDPOINT = "/api/v1/financial-parameters";

    private MockMvc mockMvc;
    private StubLoadUseCase loadUseCase;

    @BeforeEach
    void setUp() {
        loadUseCase = new StubLoadUseCase();
        SaveFinancialParametersUseCase saveUseCase = command -> null;

        JsonMapper.Builder builder = JsonMapper.builder();
        new JacksonConfig().domainScalarsCustomizer().customize(builder);
        builder.addMixIn(org.springframework.http.ProblemDetail.class,
                org.springframework.http.converter.json.ProblemDetailJacksonMixin.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new FinancialParametersController(loadUseCase, saveUseCase))
                .setControllerAdvice(new GlobalExceptionHandler(new ApiProblemDetailsFactory()))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(builder.build()))
                .build();
    }

    @Test
    void getFinancialParametersReturns200WithAllFieldsAndDecimalStrings() throws Exception {
        // T026: 200 con los 6 campos (4 editables + guarantee_deposit_rule + high_season_windows) y strings decimales (RF-009, RNF-002).
        loadUseCase.result = new FinancialParametersResult(
                7,
                new Percentage(new BigDecimal("15.00")),
                new Money(new BigDecimal("15000.00")),
                new Percentage(new BigDecimal("10.00")),
                new Percentage(new BigDecimal("25.00")),
                "10% de la tarifa base diaria",
                List.of("11-15\u219201-15", "06-01\u219207-30", "semana-santa", "10-05\u219210-12"));

        mockMvc.perform(get(ENDPOINT).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.version_number").value(7))
                .andExpect(jsonPath("$.platform_commission_percentage").value("15.00"))
                .andExpect(jsonPath("$.insurance_fee_per_passenger").value("15000.00"))
                .andExpect(jsonPath("$.weekend_increase_percentage").value("10.00"))
                .andExpect(jsonPath("$.high_season_increase_percentage").value("25.00"))
                .andExpect(jsonPath("$.guarantee_deposit_rule").value("10% de la tarifa base diaria"))
                .andExpect(jsonPath("$.high_season_windows[0]").value("11-15\u219201-15"))
                .andExpect(jsonPath("$.high_season_windows[1]").value("06-01\u219207-30"))
                .andExpect(jsonPath("$.high_season_windows[2]").value("semana-santa"))
                .andExpect(jsonPath("$.high_season_windows[3]").value("10-05\u219210-12"));
    }

    @Test
    void getFinancialParametersReturns404ProblemDetailsWhenNotConfigured() throws Exception {
        // T027: GET sin fila configurada → 404 PARAMETERS_NOT_CONFIGURED (HU3 escenario 2).
        loadUseCase.exceptionToThrow = new ContractException(
                ErrorCode.PARAMETERS_NOT_CONFIGURED,
                "The global financial parameters have not been configured yet.");

        mockMvc.perform(get(ENDPOINT).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Financial parameters not configured"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("PARAMETERS_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(jsonPath("$.detail").value("The global financial parameters have not been configured yet."));
    }

    private static class StubLoadUseCase implements LoadFinancialParametersUseCase {
        FinancialParametersResult result;
        RuntimeException exceptionToThrow;

        @Override
        public FinancialParametersResult load() {
            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }
            return result;
        }
    }
}
