package com.seashare.seasharem3.contract;

import com.seashare.seasharem3.application.dto.BaseRateResult;
import com.seashare.seasharem3.application.dto.ProvideBaseRateCommand;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato REST de UC01 en lote sobre el stack web real (CE-005 incluido): {@code @WebMvcTest}
 * usa el {@link JacksonConfig} que se cablea en produccion, no un mapper construido a mano.
 * Los servicios de aplicacion son los reales; solo se sustituyen los puertos de salida y UC02.
 */
@WebMvcTest(controllers = EstimateController.class)
@Import({EstimateConfiguration.class, JacksonConfig.class, ProblemDetailsConfig.class})
class UC01BatchEstimateContractTest {

    private static final String ENDPOINT = "/api/v1/estimates/batch";
    private static final UUID BOAT_UUID = UUID.fromString("3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01");
    private static final UUID OTHER_BOAT_UUID = UUID.fromString("7a1d2b3c-4e5f-4a6b-8c9d-0e1f2a3b4c5d");
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

    private static BaseRateResult rate(UUID boatId) {
        return new BaseRateResult(new BoatId(boatId),
                new BaseRate(new Money(new BigDecimal("350000.00"))), null);
    }

    private static String idsBody(List<UUID> ids) {
        return "{\"boat_ids\":[" + ids.stream()
                .map(id -> "\"" + id + "\"")
                .collect(Collectors.joining(",")) + "]}";
    }

    private FinancialParameters currentParameters;

    private void givenParametersAndRates(BaseRateResult... rates) {
        currentParameters = parameters();
        when(paramsRepo.findCurrent()).thenReturn(Optional.of(currentParameters));
        when(baseRateUseCase.provideBaseRates(any())).thenReturn(List.of(rates));
    }

    @Test
    void validBatchReturns200WithExactTotals() throws Exception {
        givenParametersAndRates(rate(BOAT_UUID));

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(idsBody(List.of(BOAT_UUID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluation_date").value("2026-10-25"))
                .andExpect(jsonPath("$.duration_days").value(1))
                .andExpect(jsonPath("$.passengers").value(1))
                .andExpect(jsonPath("$.estimates[0].boat_id").value(BOAT_UUID.toString()))
                .andExpect(jsonPath("$.estimates[0].estimated_total").value("365000.00"))
                .andExpect(jsonPath("$.unavailable").isEmpty());
    }

    @Test
    void emptyBatchReturns200WithEmptyArraysAndNoSideEffects() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boat_ids\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluation_date").value("2026-10-25"))
                .andExpect(jsonPath("$.duration_days").value(1))
                .andExpect(jsonPath("$.passengers").value(1))
                .andExpect(jsonPath("$.estimates").isEmpty())
                .andExpect(jsonPath("$.unavailable").isEmpty());

        verifyNoInteractions(paramsRepo, baseRateUseCase);
    }

    @Test
    void boatWithoutBaseRateIsReportedInUnavailable() throws Exception {
        givenParametersAndRates(rate(BOAT_UUID));

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(idsBody(List.of(BOAT_UUID, OTHER_BOAT_UUID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimates.length()").value(1))
                .andExpect(jsonPath("$.unavailable.length()").value(1))
                .andExpect(jsonPath("$.unavailable[0].boat_id").value(OTHER_BOAT_UUID.toString()))
                .andExpect(jsonPath("$.unavailable[0].reason").value("NO_BASE_RATE"));
    }

    @Test
    void duplicateBoatIdsQueryUc02OnlyOnce() throws Exception {
        givenParametersAndRates(rate(BOAT_UUID));

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(idsBody(List.of(BOAT_UUID, BOAT_UUID, BOAT_UUID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimates.length()").value(1))
                .andExpect(jsonPath("$.unavailable").isEmpty());

        ArgumentCaptor<ProvideBaseRateCommand> captor = ArgumentCaptor.forClass(ProvideBaseRateCommand.class);
        verify(baseRateUseCase).provideBaseRates(captor.capture());
        assertThat(captor.getValue().boatIds()).containsExactly(new BoatId(BOAT_UUID));
        assertThat(captor.getValue().evaluatedDate()).isEqualTo(TODAY);
        assertThat(captor.getValue().parameters()).isSameAs(currentParameters);
    }

    @Test
    void batchWith51IdsReturns400BatchSizeExceeded() throws Exception {
        List<UUID> ids = IntStream.range(0, 51).mapToObj(i -> UUID.randomUUID()).toList();

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(idsBody(ids)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BATCH_SIZE_EXCEEDED"));

        verifyNoInteractions(paramsRepo, baseRateUseCase);
    }

    @Test
    void batchOfExactly50IdsIsAccepted() throws Exception {
        List<UUID> ids = IntStream.range(0, 50).mapToObj(i -> UUID.randomUUID()).toList();
        givenParametersAndRates();

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(idsBody(ids)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unavailable.length()").value(50));
    }

    @Test
    void nullElementReturns400ValidationErrorNot500() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boat_ids\":[null]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(paramsRepo, baseRateUseCase);
    }

    @Test
    void invalidUuidReturns400ValidationError() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boat_ids\":[\"not-a-uuid\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void missingBoatIdsReturns400ValidationError() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("boatIds"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"start_date\":\"2026-10-25\"",
            "\"end_date\":\"2026-10-26\"",
            "\"passengers\":2",
            "\"estimate_total\":\"1.00\"",
            "\"unknown_field\":true"
    })
    void ce005_extraAttributesAreRejectedByTheProductionJacksonConfig(String extra) throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boat_ids\":[]," + extra + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(paramsRepo, baseRateUseCase);
    }

    @Test
    void fleetUnavailableReturns503() throws Exception {
        when(paramsRepo.findCurrent()).thenReturn(Optional.of(parameters()));
        when(baseRateUseCase.provideBaseRates(any()))
                .thenThrow(new BaseRateException(BaseRateFailureReason.FLEET_UNAVAILABLE, "fleet down"));

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(idsBody(List.of(BOAT_UUID))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FLEET_UNAVAILABLE"))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    void parametersNotConfiguredReturns503() throws Exception {
        when(paramsRepo.findCurrent()).thenReturn(Optional.empty());

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(idsBody(List.of(BOAT_UUID))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FINANCIAL_PARAMETERS_NOT_CONFIGURED"));

        verifyNoInteractions(baseRateUseCase);
    }

    /** El limite es configurable: la propiedad cambia el comportamiento real del servicio. */
    @Nested
    @TestPropertySource(properties = "seashare.estimates.max-batch-size=2")
    class ConfigurableBatchSizeLimit {

        @Test
        void batchOfThreeIdsReturns400BatchSizeExceeded() throws Exception {
            List<UUID> ids = IntStream.range(0, 3).mapToObj(i -> UUID.randomUUID()).toList();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(idsBody(ids)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BATCH_SIZE_EXCEEDED"));

            verifyNoInteractions(paramsRepo, baseRateUseCase);
        }

        @Test
        void batchOfTwoIdsIsAccepted() throws Exception {
            List<UUID> ids = IntStream.range(0, 2).mapToObj(i -> UUID.randomUUID()).toList();
            givenParametersAndRates();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(idsBody(ids)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.unavailable.length()").value(2));
        }
    }
}
