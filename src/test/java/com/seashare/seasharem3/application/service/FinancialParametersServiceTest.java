package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.FinancialParametersCommand;
import com.seashare.seasharem3.application.dto.FinancialParametersResult;
import com.seashare.seasharem3.application.exception.ContractException;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.service.HighSeasonCalendar;
import com.seashare.seasharem3.domain.valueobject.HighSeasonWindow;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import com.seashare.seasharem3.support.FixedClockPort;
import com.seashare.seasharem3.support.InMemoryFinancialParametersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Pruebas unitarias del servicio de aplicación UC11 (T011/T037).
 * Usa el calendario de dominio real y fakes en memoria para el repositorio y el reloj,
 * conforme al plan UC11 §Testing (nivel unitario application).
 */
class FinancialParametersServiceTest {

    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 10, 24);

    private InMemoryFinancialParametersRepository repository;
    private HighSeasonCalendar calendar;
    private FixedClockPort clock;
    private FinancialParametersService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryFinancialParametersRepository();
        calendar = new HighSeasonCalendar();
        clock = new FixedClockPort(FIXED_DATE);
        service = new FinancialParametersService(repository, calendar, clock);
    }

    // ---- load() ----

    @Test
    void loadThrowsParametersNotConfiguredWhenNoVersionExists() {
        assertThatExceptionOfType(ContractException.class)
                .isThrownBy(() -> service.load())
                .satisfies(ex -> assertThat(ex.code()).isEqualTo(ErrorCode.PARAMETERS_NOT_CONFIGURED));
    }

    @Test
    void loadReturnsCurrentVersionWithReadOnlyData() {
        givenSavedVersion(1);

        FinancialParametersResult result = service.load();

        assertThat(result.versionNumber()).isEqualTo(1);
        assertThat(result.commissionPct().value()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(result.guaranteeDepositRule()).isEqualTo(FinancialParametersService.GUARANTEE_DEPOSIT_RULE);
        assertThat(result.highSeasonWindows()).containsExactly(
                "11-15\u219201-15", "06-01\u219207-30", "semana-santa", "10-05\u219210-12");
    }

    // ---- save() ----

    @Test
    void saveCreatesVersionOneWhenNoVersionExists() {
        FinancialParametersResult result = service.save(sampleCommand());

        assertThat(result.versionNumber()).isEqualTo(1);
    }

    @Test
    void saveCreatesNewVersionWhenValuesChange() {
        givenSavedVersion(1);

        FinancialParametersCommand different = new FinancialParametersCommand(
                new Percentage(new BigDecimal("25")),
                new Money(new BigDecimal("8000")),
                new Percentage(new BigDecimal("15")),
                new Percentage(new BigDecimal("20")));

        FinancialParametersResult result = service.save(different);

        assertThat(result.versionNumber()).isEqualTo(2);
        assertThat(result.commissionPct().value()).isEqualByComparingTo(new BigDecimal("25"));
    }

    @Test
    void saveIsIdempotentWhenAllFourValuesAreIdentical() {
        // T017: idempotencia — mismos valores no crean versión nueva (D-UC11-03)
        givenSavedVersion(1);

        FinancialParametersResult first = service.save(sampleCommand());
        FinancialParametersResult second = service.save(sampleCommand());

        assertThat(first.versionNumber()).isEqualTo(second.versionNumber()).isEqualTo(1);
        assertThat(repository.findCurrent().orElseThrow().versionNumber()).isEqualTo(1);
    }

    @Test
    void saveThrowsParametersSaveFailedWhenRepositoryFails() {
        // T037: repositorio que lanza excepción → PARAMETERS_SAVE_FAILED
        InMemoryFinancialParametersRepository failingRepo = new InMemoryFinancialParametersRepository() {
            @Override
            public FinancialParameters saveNewVersion(FinancialParameters params) {
                throw new RuntimeException("simulated DB failure");
            }
        };
        FinancialParametersService failingService =
                new FinancialParametersService(failingRepo, calendar, clock);

        assertThatExceptionOfType(ContractException.class)
                .isThrownBy(() -> failingService.save(sampleCommand()))
                .satisfies(ex -> assertThat(ex.code()).isEqualTo(ErrorCode.PARAMETERS_SAVE_FAILED));
    }

    @Test
    void saveIncludesReadOnlyDataInResult() {
        FinancialParametersResult result = service.save(sampleCommand());

        assertThat(result.guaranteeDepositRule()).isEqualTo(FinancialParametersService.GUARANTEE_DEPOSIT_RULE);
        assertThat(result.highSeasonWindows()).hasSize(4);
        assertThat(result.highSeasonWindows()).contains("semana-santa");
    }

    // ---- formatWindow() ----

    @Test
    void formatWindowProducesSemanaSantaForHolyWeek() {
        HighSeasonWindow holyWeek = new HighSeasonWindow(HighSeasonWindow.Type.HOLY_WEEK,
                LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 3));

        assertThat(FinancialParametersService.formatWindow(holyWeek)).isEqualTo("semana-santa");
    }

    @Test
    void formatWindowProducesMonthDayTokenForFixedWindows() {
        HighSeasonWindow yearEnd = new HighSeasonWindow(HighSeasonWindow.Type.YEAR_END,
                LocalDate.of(2026, 11, 15), LocalDate.of(2027, 1, 15));

        assertThat(FinancialParametersService.formatWindow(yearEnd)).isEqualTo("11-15\u219201-15");
    }

    // --- helpers ---

    private void givenSavedVersion(long versionNumber) {
        repository.saveNewVersion(FinancialParameters.create(UUID.randomUUID(), versionNumber,
                new Percentage(new BigDecimal("10")),
                new Money(new BigDecimal("5000")),
                new Percentage(new BigDecimal("10")),
                new Percentage(new BigDecimal("15")),
                null, Instant.now()));
    }

    private static FinancialParametersCommand sampleCommand() {
        return new FinancialParametersCommand(
                new Percentage(new BigDecimal("10")),
                new Money(new BigDecimal("5000")),
                new Percentage(new BigDecimal("10")),
                new Percentage(new BigDecimal("15")));
    }
}
