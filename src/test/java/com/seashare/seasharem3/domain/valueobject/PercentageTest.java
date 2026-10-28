package com.seashare.seasharem3.domain.valueobject;

import com.seashare.seasharem3.domain.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class PercentageTest {

    @Test
    void acceptsTheLimits0And100() {
        assertThat(new Percentage(new BigDecimal("0")).value()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(new Percentage(new BigDecimal("100")).value()).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    void normalizesInternalScaleTo4Decimals() {
        assertThat(new Percentage(new BigDecimal("15.00")).value().scale()).isEqualTo(4);
    }

    @Test
    void roundsExcessPrecisionHalfUpTo4Decimals() {
        assertThat(new Percentage(new BigDecimal("15.12345")).value())
                .isEqualByComparingTo(new BigDecimal("15.1235"));
    }

    @Test
    void rejectsValuesOutOfRange() {
        assertThatExceptionOfType(DomainException.class).isThrownBy(() -> new Percentage(new BigDecimal("-0.01")));
        assertThatExceptionOfType(DomainException.class).isThrownBy(() -> new Percentage(new BigDecimal("100.01")));
    }

    @Test
    void rejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> new Percentage(null));
    }
}
