package com.seashare.seasharem3.contract;

import com.seashare.seasharem3.application.dto.FinancialReportExportResult;
import com.seashare.seasharem3.application.dto.FinancialReportResult;
import com.seashare.seasharem3.application.dto.GetFinancialReportCommand;
import com.seashare.seasharem3.application.port.in.ExportFinancialReportUseCase;
import com.seashare.seasharem3.application.port.in.GetFinancialReportUseCase;
import com.seashare.seasharem3.domain.model.PeriodComparison;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Periodicity;
import com.seashare.seasharem3.domain.valueobject.ReportPeriod;
import com.seashare.seasharem3.domain.valueobject.ReportScope;
import com.seashare.seasharem3.infrastructure.adapter.in.web.error.ApiProblemDetailsFactory;
import com.seashare.seasharem3.infrastructure.adapter.in.web.error.GlobalExceptionHandler;
import com.seashare.seasharem3.infrastructure.adapter.in.web.financialreport.CsvReportExporter;
import com.seashare.seasharem3.infrastructure.adapter.in.web.financialreport.FinancialReportController;
import com.seashare.seasharem3.infrastructure.adapter.in.web.financialreport.FinancialReportResponseMapper;
import com.seashare.seasharem3.infrastructure.config.web.JacksonConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UC13FinancialReportControllerContractTest {

    private MockMvc mockMvc;
    private StubUseCases useCases;

    @BeforeEach
    void setUp() {
        useCases = new StubUseCases();
        JsonMapper.Builder builder = JsonMapper.builder();
        new JacksonConfig().domainScalarsCustomizer().customize(builder);
        builder.addMixIn(org.springframework.http.ProblemDetail.class,
                org.springframework.http.converter.json.ProblemDetailJacksonMixin.class);
        FinancialReportResponseMapper mapper = new FinancialReportResponseMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(new FinancialReportController(
                        useCases, useCases, new CsvReportExporter(mapper), mapper))
                .setControllerAdvice(new GlobalExceptionHandler(new ApiProblemDetailsFactory()))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(builder.build()),
                        new StringHttpMessageConverter())
                .build();
    }

    @Test
    void rejectsUnsupportedPeriodicityAsInvalidPeriod() throws Exception {
        mockMvc.perform(get("/api/v1/financial-report")
                        .queryParam("scope", "PLATFORM")
                        .queryParam("periodicity", "YEARLY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
    }

    @Test
    void exportsTheSameFormattedMetricsAsJsonReport() throws Exception {
        mockMvc.perform(get("/api/v1/financial-report/export")
                        .queryParam("scope", "PLATFORM")
                        .queryParam("periodicity", "MONTHLY")
                        .queryParam("period", "2026-10")
                        .accept(MediaType.parseMediaType("text/csv")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "PLATFORM,MONTHLY,2026-10,30.00,100.00,10.00,60.00,5.00")));
    }

    private static final class StubUseCases
            implements GetFinancialReportUseCase, ExportFinancialReportUseCase {
        private final FinancialReportResult report = new FinancialReportResult(
                Periodicity.MONTHLY, new ReportPeriod(Periodicity.MONTHLY, "2026-10"),
                true, ReportScope.PLATFORM, money("30"), money("100"), money("10"),
                money("60"), money("5"), Map.of("CHARGE", 1),
                new PeriodComparison(new ReportPeriod(Periodicity.MONTHLY, "2026-09"),
                        money("0"), new BigDecimal("0")));

        @Override
        public FinancialReportResult get(GetFinancialReportCommand command) {
            return report;
        }

        @Override
        public FinancialReportExportResult export(GetFinancialReportCommand command) {
            return new FinancialReportExportResult(report, Instant.parse("2026-11-01T10:00:00Z"));
        }

        private static Money money(String value) {
            return new Money(new BigDecimal(value));
        }
    }
}
