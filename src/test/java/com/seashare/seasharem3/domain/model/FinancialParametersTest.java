package com.seashare.seasharem3.domain.model;

import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.exception.FinancialParametersValidationException;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class FinancialParametersTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-20T15:04:05Z");

    private static FinancialParameters create(long versionNumber) {
        return create(UUID.randomUUID(), versionNumber);
    }

    private static FinancialParameters create(UUID id, long versionNumber) {
        return FinancialParameters.create(id, versionNumber,
                new Percentage(new BigDecimal("10.00")), new Money(new BigDecimal("15000.00")),
                new Percentage(new BigDecimal("15.00")), new Percentage(new BigDecimal("20.00")),
                "admin@seashare.com", CREATED_AT);
    }

    @Test
    void createsANewVersion() {
        FinancialParameters parameters = create(1);

        assertThat(parameters.versionNumber()).isEqualTo(1);
        assertThat(parameters.commissionPct().value()).isEqualByComparingTo(new BigDecimal("10.0000"));
        assertThat(parameters.insuranceFeePerPassenger().amount())
                .isEqualByComparingTo(new BigDecimal("15000.0000"));
        assertThat(parameters.weekendIncreasePct().value()).isEqualByComparingTo(new BigDecimal("15.0000"));
        assertThat(parameters.highSeasonIncreasePct().value()).isEqualByComparingTo(new BigDecimal("20.0000"));
        assertThat(parameters.createdBy()).isEqualTo("admin@seashare.com");
        assertThat(parameters.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void equalityAndHashCodeAreValueBased() {
        UUID id = UUID.randomUUID();
        FinancialParameters first = create(id, 1);
        FinancialParameters same = create(id, 1);
        FinancialParameters otherId = create(UUID.randomUUID(), 1);
        FinancialParameters otherVersion = create(id, 2);

        assertThat(first).isEqualTo(first);
        assertThat(first).isEqualTo(same);
        assertThat(first).hasSameHashCodeAs(same);
        assertThat(first).isNotEqualTo(otherId);
        assertThat(first).isNotEqualTo(otherVersion);
        assertThat(first).isNotEqualTo("not-a-financial-parameter");
    }

    @Test
    void toStringContainsIdAndVersion() {
        UUID id = UUID.randomUUID();
        FinancialParameters parameters = create(id, 3);

        assertThat(parameters.toString())
                .contains(id.toString())
                .contains("versionNumber=3");
    }

    @Test
    void rejectsVersionLessThanOne() {
        assertThatExceptionOfType(DomainException.class).isThrownBy(() -> create(0));
    }

    @Test
    void rejectsNegativeInsuranceWithFieldDetail() {
        assertThatExceptionOfType(FinancialParametersValidationException.class)
                .isThrownBy(() -> FinancialParameters.create(
                        UUID.randomUUID(), 1, Percentage.ZERO, new Money(new BigDecimal("-1")), Percentage.ZERO,
                        Percentage.ZERO, null, Instant.now()))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("insuranceFeePerPassenger"));
    }

    @Test
    void allowsNullCreatedBy() {
        FinancialParameters parameters = FinancialParameters.create(UUID.randomUUID(), 2, Percentage.ZERO,
                Money.ZERO, Percentage.ZERO, Percentage.ZERO, null, CREATED_AT);

        assertThat(parameters.createdBy()).isNull();
    }
}
