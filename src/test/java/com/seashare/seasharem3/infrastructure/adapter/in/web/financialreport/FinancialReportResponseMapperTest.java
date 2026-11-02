package com.seashare.seasharem3.infrastructure.adapter.in.web.financialreport;

import com.seashare.seasharem3.application.dto.FinancialReportResult;
import com.seashare.seasharem3.domain.model.PeriodComparison;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Periodicity;
import com.seashare.seasharem3.domain.valueobject.ReportPeriod;
import com.seashare.seasharem3.domain.valueobject.ReportScope;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pruebas del mapeo del informe a la respuesta HTTP (UC13): los decimales monetarios salen con 2
 * posiciones y la variación porcentual se redondea HALF_UP sin lanzar excepción cuando el cociente
 * tiene más de 2 decimales.
 */
class FinancialReportResponseMapperTest {

    private final FinancialReportResponseMapper mapper = new FinancialReportResponseMapper();

    private static FinancialReportResult result(String percentageVariation) {
        BigDecimal percentage = percentageVariation == null ? null : new BigDecimal(percentageVariation);
        return new FinancialReportResult(Periodicity.MONTHLY,
                new ReportPeriod(Periodicity.MONTHLY, "2026-10"),
                true, ReportScope.PLATFORM,
                new Money(new BigDecimal("150000.00")), new Money(new BigDecimal("1150000.00")),
                new Money(new BigDecimal("100000.00")), new Money(new BigDecimal("900000.00")),
                new Money(new BigDecimal("135000.00")), Map.of("CHARGE", 1),
                new PeriodComparison(new ReportPeriod(Periodicity.MONTHLY, "2026-09"),
                        new Money(new BigDecimal("20000.00")), percentage));
    }

    @Test
    void roundsThePercentageVariationToTwoDecimalsWithoutLosingTheScale() {
        var response = mapper.toResponse(result("33.3333"));

        assertThat(response.comparison().percentageVariation()).isEqualTo("33.33");
    }

    @Test
    void doesNotFailWhenTheVariationHasMoreThanTwoDecimals() {
        assertThatCode(() -> mapper.toResponse(result("12.3456"))).doesNotThrowAnyException();
    }

    @Test
    void keepsANullVariationWhenThePreviousPeriodIsZero() {
        var response = mapper.toResponse(result(null));

        assertThat(response.comparison().percentageVariation()).isNull();
    }

    @Test
    void formatsTheMonetaryAmountsWithTwoDecimals() {
        var response = mapper.toResponse(result("10.00"));

        assertThat(response.netAmount()).isEqualTo("150000.00");
        assertThat(response.totalCommissions()).isEqualTo("135000.00");
    }
}
