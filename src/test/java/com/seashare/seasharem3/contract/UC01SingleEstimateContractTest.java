package com.seashare.seasharem3.contract;

import com.seashare.seasharem3.application.dto.BaseRateResult;
import com.seashare.seasharem3.application.dto.EstimateWarning;
import com.seashare.seasharem3.application.port.in.ProvideBaseRateUseCase;
import com.seashare.seasharem3.application.port.out.ClockPort;
import com.seashare.seasharem3.application.port.out.FinancialParametersRepository;
import com.seashare.seasharem3.domain.exception.BaseRateException;
import com.seashare.seasharem3.domain.exception.BaseRateException.BaseRateFailureReason;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import com.seashare.seasharem3.infrastructure.adapter.in.web.EstimateController;
import com.seashare.seasharem3.infrastructure.config.EstimateConfiguration;
import com.seashare.seasharem3.infrastructure.config.web.JacksonConfig;
import com.seashare.seasharem3.infrastructure.config.web.ProblemDetailsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato REST de UC01 individual sobre el stack web real: {@code @WebMvcTest} cablea el
 * {@link JacksonConfig} de produccion y los servicios de aplicacion reales; solo se sustituyen
 * los puertos de salida y UC02.
 */
@WebMvcTest(controllers = EstimateController.class)
@Import({EstimateConfiguration.class, JacksonConfig.class, ProblemDetailsConfig.class})
class UC01SingleEstimateContractTest {

    private static final String ENDPOINT = "/api/v1/estimates/individual";
    private static final UUID BOAT_UUID = UUID.fromString("3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 25);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProvideBaseRateUseCase baseRateUseCase;

    @MockitoBean
    private FinancialParametersRepository paramsRepo;

    @MockitoBean
    private ClockPort clock;

    @BeforeEach
    void setUp() {
        when(clock.today()).thenReturn(TODAY);
    }

    private static FinancialParameters parameters() {
        return FinancialParameters.create(UUID.randomUUID(), 1,
                new Percentage(new BigDecimal("10.00")),
                new Money(new BigDecimal("15000.00")),
                new Percentage(new BigDecimal("10.00")),
                new Percentage(new BigDecimal("25.00")),
                "admin", Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static BaseRateResult rate() {
        return new BaseRateResult(new BoatId(BOAT_UUID),
                new BaseRate(new Money(new BigDecimal("350000.00"))), null);
    }

    private void givenParametersAndRate() {
        when(paramsRepo.findCurrent()).thenReturn(Optional.of(parameters()));
        when(baseRateUseCase.provideBaseRates(any())).thenReturn(List.of(rate()));
    }

    private static String body(String startDate, String endDate, String passengers) {
        return """
                {"boat_id":"%s","start_date":"%s","end_date":"%s","passengers":%s}
                """.formatted(BOAT_UUID, startDate, endDate, passengers);
    }

    @Test
    void validEstimateReturns200WithExactTotalAndMandatoryWarning() throws Exception {
        givenParametersAndRate();

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("2026-10-26", "2026-10-28", "4")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.boat_id").value(BOAT_UUID.toString()))
                .andExpect(jsonPath("$.start_date").value("2026-10-26"))
                .andExpect(jsonPath("$.end_date").value("2026-10-28"))
                .andExpect(jsonPath("$.duration_days").value(3))
                .andExpect(jsonPath("$.passengers").value(4))
                // 350000 * 3 + 15000 * 4
                .andExpect(jsonPath("$.estimated_total").value("1110000.00"))
                .andExpect(jsonPath("$.warning").value(EstimateWarning.TEXT));
    }

    @Test
    void sameDayCountsAsOneDay() throws Exception {
        givenParametersAndRate();

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("2026-10-25", "2026-10-25", "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duration_days").value(1))
                .andExpect(jsonPath("$.estimated_total").value("365000.00"));
    }

    @Test
    void startDateInThePastReturns400InvalidDateRange() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("2026-10-24", "2026-10-28", "1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));

        verifyNoInteractions(paramsRepo, baseRateUseCase);
    }

    @Test
    void endDateBeforeStartDateReturns400InvalidDateRange() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("2026-10-28", "2026-10-26", "1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));

        verifyNoInteractions(paramsRepo, baseRateUseCase);
    }

    @Test
    void malformedDateReturns400InvalidDateRange() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("26/10/2026", "2026-10-28", "1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));

        verifyNoInteractions(paramsRepo, baseRateUseCase);
    }

    @Test
    void zeroPassengersReturns400ValidationError() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("2026-10-26", "2026-10-28", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(paramsRepo, baseRateUseCase);
    }

    @Test
    void missingBoatRateReturns422BaseRateNotAvailable() throws Exception {
        when(paramsRepo.findCurrent()).thenReturn(Optional.of(parameters()));
        when(baseRateUseCase.provideBaseRates(any())).thenReturn(List.of());

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("2026-10-26", "2026-10-28", "1")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("BASE_RATE_NOT_AVAILABLE"));
    }

    @Test
    void fleetUnavailableReturns503() throws Exception {
        when(paramsRepo.findCurrent()).thenReturn(Optional.of(parameters()));
        when(baseRateUseCase.provideBaseRates(any()))
                .thenThrow(new BaseRateException(BaseRateFailureReason.FLEET_UNAVAILABLE, "fleet down"));

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("2026-10-26", "2026-10-28", "1")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FLEET_UNAVAILABLE"))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    void parametersNotConfiguredReturns503() throws Exception {
        when(paramsRepo.findCurrent()).thenReturn(Optional.empty());

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("2026-10-26", "2026-10-28", "1")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FINANCIAL_PARAMETERS_NOT_CONFIGURED"));

        verifyNoInteractions(baseRateUseCase);
    }
}
