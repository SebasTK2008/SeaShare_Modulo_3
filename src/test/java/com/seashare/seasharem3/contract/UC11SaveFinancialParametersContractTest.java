package com.seashare.seasharem3.contract;

import com.seashare.seasharem3.application.dto.FinancialParametersCommand;
import com.seashare.seasharem3.application.dto.SaveFinancialParametersResult;
import com.seashare.seasharem3.application.exception.ContractException;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.LoadFinancialParametersUseCase;
import com.seashare.seasharem3.application.port.in.SaveFinancialParametersUseCase;
import com.seashare.seasharem3.domain.exception.FinancialParametersValidationException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
        saveUseCase.resultToReturn = new SaveFinancialParametersResult(8);

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
        assertThat(saveUseCase.receivedCommand.commissionPct().value()).isEqualByComparingTo("15.00");
        assertThat(saveUseCase.receivedCommand.insuranceFeePerPassenger().amount()).isEqualByComparingTo("15000.00");
    }

    @Test
    void saveWithOutOfRangeValueReturns400WithFieldDetail() throws Exception {
        // T021: PUT con valor fuera de rango (>100 %) -> 400 VALIDATION_ERROR con detalle de campo
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
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.errors[0].field").value("platform_commission_percentage"))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("0-100")));

        assertThat(saveUseCase.receivedCommand).isNull();
    }

    @Test
    void saveWithNegativeInsuranceFeeReturns400WithFieldDetail() throws Exception {
        // RF-013: tarifa del seguro náutico >= 0; la validación vive en el dominio.
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
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.errors[0].field").value("insurance_fee_per_passenger"))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("greater than or equal to 0")));
    }

    @Test
    void saveWithNonNumericValuesReturns400WithFieldDetail() throws Exception {
        // RF-013: formato no numérico en los cuatro campos.
        assertFieldError("abc", "platform_commission_percentage");
        assertFieldError("abc", "insurance_fee_per_passenger");
        assertFieldError("abc", "weekend_increase_percentage");
        assertFieldError("abc", "high_season_increase_percentage");
    }

    @Test
    void saveWithJsonNumbersReturns400WithFieldDetail() throws Exception {
        assertJsonNumberError("platform_commission_percentage", "15.00");
        assertJsonNumberError("insurance_fee_per_passenger", "15000.00");
        assertJsonNumberError("weekend_increase_percentage", "10.00");
        assertJsonNumberError("high_season_increase_percentage", "25.00");
    }

    @Test
    void saveAcceptsBoundaryValues() throws Exception {
        // RF-013: 0 y 100 son inclusivos.
        saveUseCase.resultToReturn = new SaveFinancialParametersResult(1);

        String requestBody = """
                {
                  "platform_commission_percentage": "0.00",
                  "insurance_fee_per_passenger": "0.00",
                  "weekend_increase_percentage": "0.00",
                  "high_season_increase_percentage": "100.00"
                }
                """;

        mockMvc.perform(put(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version_number").value(1));
    }

    @Test
    void saveWithMissingRequiredFieldsReturns400ValidationError() throws Exception {
        // RF-013: ningún campo obligatorio puede faltar ni ser null
        String requestBody = """
                {
                  "platform_commission_percentage": "15.00",
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
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.errors[0].field").value("insurance_fee_per_passenger"));

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

    private void assertFieldError(String value, String field) throws Exception {
        String commission = field.equals("platform_commission_percentage") ? value : "15.00";
        String insurance = field.equals("insurance_fee_per_passenger") ? value : "15000.00";
        String weekend = field.equals("weekend_increase_percentage") ? value : "10.00";
        String highSeason = field.equals("high_season_increase_percentage") ? value : "25.00";
        String requestBody = """
                {
                  "platform_commission_percentage": "%s",
                  "insurance_fee_per_passenger": "%s",
                  "weekend_increase_percentage": "%s",
                  "high_season_increase_percentage": "%s"
                }
                """.formatted(commission, insurance, weekend, highSeason);

        mockMvc.perform(put(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("decimal")));
    }

    private void assertJsonNumberError(String field, String number) throws Exception {
        String commission = field.equals("platform_commission_percentage") ? number : "\"15.00\"";
        String insurance = field.equals("insurance_fee_per_passenger") ? number : "\"15000.00\"";
        String weekend = field.equals("weekend_increase_percentage") ? number : "\"10.00\"";
        String highSeason = field.equals("high_season_increase_percentage") ? number : "\"25.00\"";
        String requestBody = """
                {
                  "platform_commission_percentage": %s,
                  "insurance_fee_per_passenger": %s,
                  "weekend_increase_percentage": %s,
                  "high_season_increase_percentage": %s
                }
                """.formatted(commission, insurance, weekend, highSeason);

        mockMvc.perform(put(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("decimal")));
    }

    private static class StubSaveUseCase implements SaveFinancialParametersUseCase {
        FinancialParametersCommand receivedCommand;
        SaveFinancialParametersResult resultToReturn;
        RuntimeException exceptionToThrow;

        @Override
        public SaveFinancialParametersResult save(FinancialParametersCommand command) {
            this.receivedCommand = command;
            if (command.insuranceFeePerPassenger().isNegative()) {
                throw new FinancialParametersValidationException("insuranceFeePerPassenger",
                        "The insurance fee per passenger must be greater than or equal to 0: "
                                + command.insuranceFeePerPassenger().amount());
            }
            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }
            return resultToReturn;
        }
    }
}
