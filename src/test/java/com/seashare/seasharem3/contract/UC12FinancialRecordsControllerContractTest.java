package com.seashare.seasharem3.contract;

import com.seashare.seasharem3.application.dto.FinancialRecordResult;
import com.seashare.seasharem3.application.dto.FinancialRecordsPageResult;
import com.seashare.seasharem3.application.port.in.QueryFinancialRecordsUseCase;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.FinancialRecordType;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.infrastructure.adapter.in.web.FinancialRecordsController;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UC12FinancialRecordsControllerContractTest {

    private MockMvc mockMvc;
    private StubUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new StubUseCase();
        JsonMapper.Builder builder = JsonMapper.builder();
        new JacksonConfig().domainScalarsCustomizer().customize(builder);
        builder.addMixIn(org.springframework.http.ProblemDetail.class,
                org.springframework.http.converter.json.ProblemDetailJacksonMixin.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new FinancialRecordsController(useCase))
                .setControllerAdvice(new GlobalExceptionHandler(new ApiProblemDetailsFactory()))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(builder.build()))
                .build();
    }

    @Test
    void returnsPagedRecordsWithContractNamesAndStringAmount() throws Exception {
        UUID reservation = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID boat = UUID.randomUUID();
        useCase.result = new FinancialRecordsPageResult(List.of(new FinancialRecordResult(
                FinancialRecordType.COMMISSION, new ReservationId(reservation), new OwnerId(owner),
                new BoatId(boat), new Money(new BigDecimal("12.3456")),
                Instant.parse("2026-10-01T10:00:00Z"), null)), 1, 10, 1, 1);

        mockMvc.perform(get("/api/v1/financial-records")
                        .queryParam("scope", "OWNER")
                        .queryParam("owner_id", owner.toString())
                        .queryParam("page", "1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].record_type").value("COMMISSION"))
                .andExpect(jsonPath("$.content[0].amount").value("12.35"))
                .andExpect(jsonPath("$.page_size").value(10))
                .andExpect(jsonPath("$.total_elements").value(1));
    }

    @Test
    void rejectsInvalidTransactionTypeAsValidationProblem() throws Exception {
        mockMvc.perform(get("/api/v1/financial-records")
                        .queryParam("scope", "PLATFORM")
                        .queryParam("page", "1")
                        .queryParam("transaction_type", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private static final class StubUseCase implements QueryFinancialRecordsUseCase {
        private FinancialRecordsPageResult result;

        @Override
        public FinancialRecordsPageResult query(
                com.seashare.seasharem3.application.dto.QueryFinancialRecordsCommand command) {
            return result;
        }
    }
}
