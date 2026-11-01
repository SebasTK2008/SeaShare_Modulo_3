package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.FinancialReportResult;
import com.seashare.seasharem3.application.dto.GetFinancialReportCommand;
import com.seashare.seasharem3.application.port.out.ClockPort;
import com.seashare.seasharem3.application.port.out.FinancialReportQueryPort;
import com.seashare.seasharem3.domain.model.FinancialRecord;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.FinancialRecordType;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.Periodicity;
import com.seashare.seasharem3.domain.valueobject.ReportPeriod;
import com.seashare.seasharem3.domain.valueobject.ReportScope;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.domain.service.ReportPeriodResolver;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialReportServiceTest {

    @Test
    void calculatesPlatformNetWithoutDoubleCountingCommission() {
        OwnerId owner = new OwnerId(UUID.randomUUID());
        CapturingQueryPort queryPort = new CapturingQueryPort(List.of(
                record(FinancialRecordType.CHARGE, "100.00", owner),
                record(FinancialRecordType.REFUND, "10.00", owner),
                record(FinancialRecordType.SETTLEMENT, "60.00", owner),
                record(FinancialRecordType.COMMISSION, "5.00", owner)));
        FinancialReportService service = service(queryPort, LocalDate.of(2026, 11, 1));

        FinancialReportResult result = service.get(new GetFinancialReportCommand(
                Periodicity.MONTHLY, ReportScope.PLATFORM, null,
                new ReportPeriod(Periodicity.MONTHLY, "2026-10")));

        assertThat(result.netAmount().amount()).isEqualByComparingTo("30.0000");
        assertThat(result.totalCommissions().amount()).isEqualByComparingTo("5.0000");
        assertThat(result.counts()).containsEntry("CHARGE", 1).containsEntry("COMMISSION", 1);
        assertThat(queryPort.commands).hasSize(2);
    }

    @Test
    void calculatesOwnerEarningsAndUsesPreviousEquivalentPeriod() {
        OwnerId owner = new OwnerId(UUID.randomUUID());
        CapturingQueryPort queryPort = new CapturingQueryPort(List.of(
                record(FinancialRecordType.SETTLEMENT, "60.00", owner)));
        FinancialReportService service = service(queryPort, LocalDate.of(2026, 11, 1));

        FinancialReportResult result = service.get(new GetFinancialReportCommand(
                Periodicity.MONTHLY, ReportScope.OWNER, owner,
                new ReportPeriod(Periodicity.MONTHLY, "2026-10")));

        assertThat(result.netAmount().amount()).isEqualByComparingTo("60.0000");
        assertThat(result.comparison().previousPeriod().value()).isEqualTo("2026-09");
        assertThat(queryPort.commands.get(0).ownerId()).isEqualTo(owner);
        assertThat(queryPort.commands.get(1).period().value()).isEqualTo("2026-09");
    }

    @Test
    void defaultsToLastClosedPeriod() {
        CapturingQueryPort queryPort = new CapturingQueryPort(List.of());
        FinancialReportService service = service(queryPort, LocalDate.of(2026, 11, 1));

        FinancialReportResult result = service.get(new GetFinancialReportCommand(
                Periodicity.SEMI_MONTHLY, ReportScope.PLATFORM, null, null));

        assertThat(result.period().value()).isEqualTo("2026-10-H2");
        assertThat(result.isClosed()).isTrue();
    }

    @Test
    void usesFixedSemiMonthlyBoundaries() {
        ReportPeriodResolver resolver = new ReportPeriodResolver(
                () -> LocalDate.of(2026, 10, 20));

        assertThat(resolver.bounds(new ReportPeriod(Periodicity.SEMI_MONTHLY,
                "2026-10-H1")).start()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(resolver.bounds(new ReportPeriod(Periodicity.SEMI_MONTHLY,
                "2026-10-H1")).endExclusive()).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(resolver.bounds(new ReportPeriod(Periodicity.SEMI_MONTHLY,
                "2026-10-H2")).endExclusive()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void reportsOpenEmptyPeriodAndNullPercentageWhenPreviousValueIsZero() {
        CapturingQueryPort queryPort = new CapturingQueryPort(List.of());
        FinancialReportService service = service(queryPort, LocalDate.of(2026, 10, 10));

        FinancialReportResult result = service.get(new GetFinancialReportCommand(
                Periodicity.MONTHLY, ReportScope.PLATFORM, null,
                new ReportPeriod(Periodicity.MONTHLY, "2026-10")));

        assertThat(result.isClosed()).isFalse();
        assertThat(result.netAmount().isZero()).isTrue();
        assertThat(result.comparison().percentageVariation()).isNull();
        assertThat(result.counts()).containsEntry("CHARGE", 0)
                .containsEntry("REFUND", 0)
                .containsEntry("SETTLEMENT", 0)
                .containsEntry("COMMISSION", 0);
    }

    private static FinancialReportService service(FinancialReportQueryPort queryPort, LocalDate today) {
        ClockPort clockPort = () -> today;
        return new FinancialReportService(queryPort, clockPort,
                Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    private static FinancialRecord record(FinancialRecordType type, String amount, OwnerId owner) {
        return new FinancialRecord(UUID.randomUUID(), type, new ReservationId(UUID.randomUUID()),
                owner, new BoatId(UUID.randomUUID()), new Money(new BigDecimal(amount)),
                Instant.parse("2026-10-10T12:00:00Z"), null);
    }

    private static final class CapturingQueryPort implements FinancialReportQueryPort {
        private final List<FinancialRecord> records;
        private final List<GetFinancialReportCommand> commands = new ArrayList<>();

        private CapturingQueryPort(List<FinancialRecord> records) {
            this.records = records;
        }

        @Override
        public List<FinancialRecord> query(GetFinancialReportCommand command) {
            commands.add(command);
            return records;
        }
    }
}
