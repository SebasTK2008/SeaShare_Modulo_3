package com.seashare.seasharem3.domain.service;

import com.seashare.seasharem3.domain.valueobject.Periodicity;
import com.seashare.seasharem3.domain.valueobject.ReportPeriod;

import java.time.LocalDate;
import java.time.Month;
import java.util.Objects;
import java.util.function.Supplier;

public final class ReportPeriodResolver {

    public record PeriodBounds(LocalDate start, LocalDate endExclusive, boolean closed) {
    }

    private final Supplier<LocalDate> today;

    public ReportPeriodResolver(Supplier<LocalDate> today) {
        this.today = Objects.requireNonNull(today, "today");
    }

    public ReportPeriod latestClosed(Periodicity periodicity) {
        Objects.requireNonNull(periodicity, "periodicity");
        LocalDate current = today();
        return switch (periodicity) {
            case SEMI_MONTHLY -> current.getDayOfMonth() <= 15
                    ? new ReportPeriod(periodicity, formatMonth(current.minusMonths(1)) + "-H2")
                    : new ReportPeriod(periodicity, formatMonth(current) + "-H1");
            case MONTHLY -> {
                LocalDate month = current.withDayOfMonth(1).minusMonths(1);
                yield new ReportPeriod(periodicity, formatMonth(month));
            }
            case QUARTERLY -> {
                int quarter = (current.getMonthValue() - 1) / 3 + 1;
                LocalDate start = LocalDate.of(current.getYear(),
                        Month.of((quarter - 1) * 3 + 1), 1).minusMonths(3);
                yield new ReportPeriod(periodicity, "%d-Q%d".formatted(start.getYear(),
                        (start.getMonthValue() - 1) / 3 + 1));
            }
        };
    }

    public ReportPeriod previous(ReportPeriod period) {
        PeriodBounds bounds = bounds(period);
        LocalDate previousStart = switch (period.periodicity()) {
            case SEMI_MONTHLY -> bounds.start().getDayOfMonth() == 1
                    ? bounds.start().minusMonths(1).withDayOfMonth(16)
                    : bounds.start().withDayOfMonth(1);
            case MONTHLY -> bounds.start().minusMonths(1);
            case QUARTERLY -> bounds.start().minusMonths(3);
        };
        return period(period.periodicity(), previousStart);
    }

    public PeriodBounds bounds(ReportPeriod period) {
        Objects.requireNonNull(period, "period");
        String value = period.value();
        try {
            LocalDate start;
            switch (period.periodicity()) {
                case SEMI_MONTHLY -> {
                    if (!value.matches("\\d{4}-\\d{2}-H[12]")) throw invalid();
                    LocalDate month = LocalDate.of(Integer.parseInt(value.substring(0, 4)),
                            Integer.parseInt(value.substring(5, 7)), 1);
                    start = "H1".equals(value.substring(8)) ? month : month.withDayOfMonth(16);
                }
                case MONTHLY -> {
                    if (!value.matches("\\d{4}-\\d{2}")) throw invalid();
                    start = LocalDate.of(Integer.parseInt(value.substring(0, 4)),
                            Integer.parseInt(value.substring(5, 7)), 1);
                }
                case QUARTERLY -> {
                    if (!value.matches("\\d{4}-Q[1-4]")) throw invalid();
                    int year = Integer.parseInt(value.substring(0, 4));
                    int quarter = Integer.parseInt(value.substring(6));
                    start = LocalDate.of(year, (quarter - 1) * 3 + 1, 1);
                }
                default -> throw invalid();
            }
            LocalDate end = switch (period.periodicity()) {
                case SEMI_MONTHLY -> start.getDayOfMonth() == 1
                        ? start.withDayOfMonth(16) : start.plusMonths(1).withDayOfMonth(1);
                case MONTHLY -> start.plusMonths(1);
                case QUARTERLY -> start.plusMonths(3);
            };
            return new PeriodBounds(start, end, !today().isBefore(end));
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException) throw exception;
            throw invalid();
        }
    }

    private LocalDate today() {
        return today.get();
    }

    private static ReportPeriod period(Periodicity periodicity, LocalDate start) {
        return switch (periodicity) {
            case SEMI_MONTHLY -> new ReportPeriod(periodicity,
                    formatMonth(start) + (start.getDayOfMonth() == 1 ? "-H1" : "-H2"));
            case MONTHLY -> new ReportPeriod(periodicity, formatMonth(start));
            case QUARTERLY -> new ReportPeriod(periodicity, "%d-Q%d".formatted(start.getYear(),
                    (start.getMonthValue() - 1) / 3 + 1));
        };
    }

    private static String formatMonth(LocalDate date) {
        return "%04d-%02d".formatted(date.getYear(), date.getMonthValue());
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid report period.");
    }
}
