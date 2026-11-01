package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.FinancialReportExportResult;
import com.seashare.seasharem3.application.dto.FinancialReportResult;
import com.seashare.seasharem3.application.dto.GetFinancialReportCommand;
import com.seashare.seasharem3.application.exception.ApiFieldError;
import com.seashare.seasharem3.application.exception.ContractException;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.ExportFinancialReportUseCase;
import com.seashare.seasharem3.application.port.in.GetFinancialReportUseCase;
import com.seashare.seasharem3.application.port.out.ClockPort;
import com.seashare.seasharem3.application.port.out.FinancialReportQueryPort;
import com.seashare.seasharem3.domain.model.PeriodComparison;
import com.seashare.seasharem3.domain.model.ReportMetrics;
import com.seashare.seasharem3.domain.service.FinancialAggregator;
import com.seashare.seasharem3.domain.service.ReportPeriodResolver;
import com.seashare.seasharem3.domain.service.VariationCalculator;
import com.seashare.seasharem3.domain.valueobject.Periodicity;
import com.seashare.seasharem3.domain.valueobject.ReportPeriod;
import com.seashare.seasharem3.domain.valueobject.ReportScope;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class FinancialReportService implements GetFinancialReportUseCase, ExportFinancialReportUseCase {

    private final FinancialReportQueryPort queryPort;
    private final ReportPeriodResolver periodResolver;
    private final FinancialAggregator aggregator;
    private final VariationCalculator variationCalculator;
    private final Clock instantClock;

    public FinancialReportService(FinancialReportQueryPort queryPort, ClockPort clockPort,
                                  Clock instantClock) {
        this.queryPort = Objects.requireNonNull(queryPort, "queryPort");
        this.periodResolver = new ReportPeriodResolver(clockPort::today);
        this.aggregator = new FinancialAggregator();
        this.variationCalculator = new VariationCalculator();
        this.instantClock = Objects.requireNonNull(instantClock, "instantClock");
    }

    @Override
    @Transactional(readOnly = true)
    public FinancialReportResult get(GetFinancialReportCommand command) {
        validate(command);
        ReportPeriod period = command.period() == null
                ? periodResolver.latestClosed(command.periodicity()) : command.period();
        validatePeriod(command.periodicity(), period);
        return calculate(command, period);
    }

    @Override
    @Transactional(readOnly = true)
    public FinancialReportExportResult export(GetFinancialReportCommand command) {
        validate(command);
        if (command.period() == null) validation("period", "period is required for export.");
        ReportPeriod period = command.period();
        validatePeriod(command.periodicity(), period);
        return new FinancialReportExportResult(calculate(command, period), Instant.now(instantClock));
    }

    private FinancialReportResult calculate(GetFinancialReportCommand command, ReportPeriod period) {
        GetFinancialReportCommand normalized = new GetFinancialReportCommand(
                command.periodicity(), command.scope(), command.ownerId(), period);
        ReportMetrics current = aggregator.aggregate(queryPort.query(normalized), command.scope());
        ReportPeriod previousPeriod = periodResolver.previous(period);
        ReportMetrics previous = aggregator.aggregate(queryPort.query(new GetFinancialReportCommand(
                command.periodicity(), command.scope(), command.ownerId(), previousPeriod)),
                command.scope());
        PeriodComparison comparison = variationCalculator.compare(previousPeriod,
                current.netAmount(), previous.netAmount());
        return new FinancialReportResult(command.periodicity(), period,
                periodResolver.bounds(period).closed(), command.scope(), current.netAmount(),
                current.totalCollections(), current.totalRefunds(), current.totalSettlements(),
                current.totalCommissions(), current.counts(), comparison);
    }

    private static void validate(GetFinancialReportCommand command) {
        if (command == null || command.periodicity() == null) {
            validation("periodicity", "periodicity is required.");
        }
        if (command.scope() == null) validation("scope", "scope is required.");
        if (command.scope() == ReportScope.OWNER && command.ownerId() == null) {
            validation("owner_id", "owner_id is required when scope is OWNER.");
        }
    }

    private void validatePeriod(Periodicity periodicity, ReportPeriod period) {
        if (period == null || period.periodicity() != periodicity) {
            throw new ContractException(ErrorCode.INVALID_PERIOD, "period does not match periodicity.");
        }
        try {
            periodResolver.bounds(period);
        } catch (IllegalArgumentException exception) {
            throw new ContractException(ErrorCode.INVALID_PERIOD, exception.getMessage(), exception);
        }
    }

    private static void validation(String field, String message) {
        throw new ContractException(ErrorCode.VALIDATION_ERROR, message,
                List.of(new ApiFieldError(field, message)));
    }
}
