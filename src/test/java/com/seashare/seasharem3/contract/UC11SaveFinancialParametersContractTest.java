package com.seashare.seasharem3.contract;

import com.seashare.seasharem3.application.dto.FinancialParametersCommand;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de contrato del endpoint {@code PUT /api/v1/financial-parameters}
 * (T010, T021, T036, T041).
 * Fuente: {@code contracts/rest/UC11-guardar-parametros-financieros.md} y SPEC 11 HU1, HU2, HU4.
 */
class UC11SaveFinancialParametersContractTest {

    private static final String ENDPOINT = "/api/v1/financial-parameters";

    private MockMvc mockMvc;
    private StubSaveUseCase saveUseCase;

    @BeforeEach
    void setUp() {
        LoadFinancialParametersUseCase loadUseCase = () -> null;
        saveUseCase = new StubSaveUseCase();

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
    void saveValidParametersReturns200WithVersionNumber() throws Exception {
        // T010 / T041: PUT válido -> 200 OK con { version_number: N }, string decimal aceptado (RNF-002)
        saveUseCase.resultToReturn = new FinancialParametersResult(
                8,
                new Percentage(new BigDecimal("15.00")),
                new Money(new BigDecimal("15000.00")),
                new Percentage(new BigDecimal("10.00")),
                new Percentage(new BigDecimal("25.00")),
                "10% de la tarifa base diaria",
                List.of("11-15\u219201-15", "06-01\u219207-30", "semana-santa", "10-05\u219210-12"));

        String requestBody = """
                {
                  "platform_commission_percentage": "15.00",
                  "insurance_fee_per_passenger": "15000.00",
                  "weekend_increase_percentage": "10.00",
                  "high_season_increase_percentage": "25.00"
                }
                """;

        mockMvc.perform(put(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.version_number").value(8));

        assertThat(saveUseCase.receivedCommand).isNotNull();
        assertThat(saveUseCase.receivedCommand.commissionPct().value()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(saveUseCase.receivedCommand.insuranceFeePerPassenger().amount()).isEqualByComparingTo(new BigDecimal("15000.00"));
    }

    @Test
    void saveWithOutOfRangeValueReturns400ValidationError() throws Exception {
        // T021: PUT con valor fuera de rango (>100 %) -> 400 VALIDATION_ERROR y sin persistencia (CE-004)
        String requestBody = """
                {
                  "platform_commission_percentage": "150.00",
                  "insurance_fee_per_passenger": "15000.00",
                  "weekend_increase_percentage": "10.00",
                  "high_season_increase_percentage": "25.00"
                }
                """;

        mockMvc.perform(put(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.retryable").value(false));

        assertThat(saveUseCase.receivedCommand).isNull();
    }

    @Test
    void saveWithNegativeInsuranceFeeReturns400ValidationError() throws Exception {
        // RF-013: tarifa del seguro náutico >= 0
        String requestBody = """
                {
                  "platform_commission_percentage": "15.00",
                  "insurance_fee_per_passenger": "-1.00",
                  "weekend_increase_percentage": "10.00",
                  "high_season_increase_percentage": "25.00"
                }
                """;

        mockMvc.perform(put(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.retryable").value(false));

        assertThat(saveUseCase.receivedCommand).isNull();
    }

    @Test
    void saveWithMissingRequiredFieldsReturns400ValidationError() throws Exception {
        // RF-013: ningún campo obligatorio puede faltar ni ser null
        String requestBody = """
                {
                  "platform_commission_percentage": "15.00"
                }
                """;

        mockMvc.perform(put(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.retryable").value(false));

        assertThat(saveUseCase.receivedCommand).isNull();
    }

    @Test
    void saveFailsWith500ParametersSaveFailedWhenPersistenceFails() throws Exception {
        // HU4 esc. 3: fallo durante guardado -> 500 PARAMETERS_SAVE_FAILED
        saveUseCase.exceptionToThrow = new ContractException(
                ErrorCode.PARAMETERS_SAVE_FAILED,
                "The financial parameters could not be saved. The previous configuration has been preserved.");

        String requestBody = """
                {
                  "platform_commission_percentage": "15.00",
                  "insurance_fee_per_passenger": "15000.00",
                  "weekend_increase_percentage": "10.00",
                  "high_season_increase_percentage": "25.00"
                }
                """;

        mockMvc.perform(put(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("PARAMETERS_SAVE_FAILED"))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    private static class StubSaveUseCase implements SaveFinancialParametersUseCase {
        FinancialParametersCommand receivedCommand;
        FinancialParametersResult resultToReturn;
        RuntimeException exceptionToThrow;

        @Override
        public FinancialParametersResult save(FinancialParametersCommand command) {
            this.receivedCommand = command;
            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }
            return resultToReturn;
        }
    }
}
