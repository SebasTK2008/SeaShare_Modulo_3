package com.seashare.seasharem3.domain.service;

import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.valueobject.HighSeasonWindow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class HighSeasonCalendarTest {

    private final HighSeasonCalendar calendar = new HighSeasonCalendar();

    @ParameterizedTest
    @ValueSource(strings = {
            "2026-01-01",
            "2026-01-15",
            "2026-04-02",
            "2026-04-03",
            "2026-06-01",
            "2026-07-30",
            "2026-10-05",
            "2026-10-12",
            "2026-11-15",
            "2026-12-20",
            "2027-01-15"
    })
    void recognizesHighSeasonDates(String date) {
        assertThat(calendar.isHighSeason(LocalDate.parse(date))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2026-01-16",
            "2026-04-01",
            "2026-04-04",
            "2026-04-05",
            "2026-05-31",
            "2026-07-31",
            "2026-10-04",
            "2026-10-13",
            "2026-11-14",
            "2027-01-16"
    })
    void rejectsDatesOutsideHighSeasonWindows(String date) {
        assertThat(calendar.isHighSeason(LocalDate.parse(date))).isFalse();
    }

    @Test
    void returnsTheFourWindowsForAYear() {
        List<HighSeasonWindow> windows = calendar.windowsForYear(2026);

        assertThat(windows).containsExactly(
                new HighSeasonWindow(HighSeasonWindow.Type.YEAR_END,
                        LocalDate.of(2026, 11, 15), LocalDate.of(2027, 1, 15)),
                new HighSeasonWindow(HighSeasonWindow.Type.MID_YEAR,
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 30)),
                new HighSeasonWindow(HighSeasonWindow.Type.HOLY_WEEK,
                        LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 3)),
                new HighSeasonWindow(HighSeasonWindow.Type.RECESS_WEEK,
                        LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12)));
    }

    @Test
    void rejectsYearsBeforeTheGregorianCalendar() {
        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> calendar.windowsForYear(1582))
                .withMessageContaining("1583");
    }
}
