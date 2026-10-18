package com.seashare.seasharem3.domain.model;

import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class FinancialParametersTest {

    private static FinancialParameters create(long versionNumber) {
        return FinancialParameters.create(UUID.randomUUID(), versionNumber,
                new Percentage(new BigDecimal("10.00")), new Money(new BigDecimal("15000.00")),
                new Percentage(new BigDecimal("15.00")), new Percentage(new BigDecimal("20.00")),
                "admin@seashare.com", Instant.parse("2026-09-20T15:04:05Z"));
    }

    @Test
    void createsANewVersion() {
        FinancialParameters parameters = create(1);

        assertThat(parameters.versionNumber()).isEqualTo(1);
        assertThat(parameters.commissionPct().value()).isEqualByComparingTo(new BigDecimal("10.0000"));
        assertThat(parameters.insuranceFeePerPassenger().amount())
                .isEqualByComparingTo(new BigDecimal("15000.0000"));
    }

    @Test
    void rejectsVersionLessThanOne() {
        assertThatExceptionOfType(DomainException.class).isThrownBy(() -> create(0));
    }

    @Test
    void rejectsNegativeInsurance() {
        assertThatExceptionOfType(DomainException.class).isThrownBy(() -> FinancialParameters.create(
                UUID.randomUUID(), 1, Percentage.ZERO, new Money(new BigDecimal("-1")), Percentage.ZERO,
                Percentage.ZERO, null, Instant.now()));
    }

    @Test
    void allowsNullCreatedBy() {
        FinancialParameters parameters = FinancialParameters.create(UUID.randomUUID(), 2, Percentage.ZERO,
                Money.ZERO, Percentage.ZERO, Percentage.ZERO, null, Instant.parse("2026-09-20T15:04:05Z"));

        assertThat(parameters.createdBy()).isNull();
    }
}
