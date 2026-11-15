package com.seashare.seasharem3.domain.service;

import com.seashare.seasharem3.domain.exception.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class EasterCalculatorTest {

    @ParameterizedTest
    @CsvSource({
            "2024, 2024-03-31",
            "2025, 2025-04-20",
            "2026, 2026-04-05",
            "2027, 2027-03-28",
            "2028, 2028-04-16",
            "2029, 2029-04-01",
            "2030, 2030-04-21"
    })
    void calculatesKnownEasterSundays(int year, String expectedDate) {
        assertThat(EasterCalculator.easterSunday(year)).isEqualTo(LocalDate.parse(expectedDate));
    }

    @Test
    void rejectsYearsBeforeTheGregorianCalendar() {
        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> EasterCalculator.easterSunday(1582))
                .withMessageContaining("1583");
    }
}
