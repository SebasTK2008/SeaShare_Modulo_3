package com.seashare.seasharem3.acceptance;

import com.seashare.seasharem3.application.dto.FinancialReportExportResult;
import com.seashare.seasharem3.application.dto.FinancialReportResult;
import com.seashare.seasharem3.application.dto.GetFinancialReportCommand;
import com.seashare.seasharem3.application.port.out.ClockPort;
import com.seashare.seasharem3.application.port.out.FinancialReportQueryPort;
import com.seashare.seasharem3.application.service.FinancialReportService;
import com.seashare.seasharem3.domain.model.FinancialRecord;
import com.seashare.seasharem3.domain.model.ReportMetrics;
import com.seashare.seasharem3.domain.service.FinancialAggregator;
import com.seashare.seasharem3.domain.service.ReportPeriodResolver;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.FinancialRecordType;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.Periodicity;
import com.seashare.seasharem3.domain.valueobject.ReportPeriod;
import com.seashare.seasharem3.domain.valueobject.ReportScope;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.infrastructure.adapter.in.web.financialreport.CsvReportExporter;
import com.seashare.seasharem3.infrastructure.adapter.in.web.financialreport.FinancialReportResponseMapper;
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

/**
 * Pruebas de aceptación de UC13 (CE-001…CE-006) sobre el servicio real y una fuente en memoria:
 * alcance declarado, neto sin doble conteo, comparación con el período anterior equivalente,
 * exclusión de pendientes y equivalencia entre el informe y su CSV.
 *
 * <p>Fuente: UC13 plan T012-T016 y SPEC 13 Criterios de Éxito.</p>
 */
class Uc13AcceptanceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);
    private static final OwnerId OWNER = new OwnerId(UUID.randomUUID());

    private static FinancialRecord record(FinancialRecordType type, String amount, Instant createdAt) {
        return new FinancialRecord(UUID.randomUUID(), type, new ReservationId(UUID.randomUUID()), OWNER,
                new BoatId(UUID.randomUUID()), new Money(new BigDecimal(amount)), createdAt, null);
    }

    private static FinancialReportService service(List<FinancialRecord> records) {
        ClockPort clockPort = () -> TODAY;
        return new FinancialReportService(new InMemoryQueryPort(records), clockPort,
                Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    private static GetFinancialReportCommand command(ReportScope scope, String period) {
        return new GetFinancialReportCommand(Periodicity.MONTHLY, scope,
                scope == ReportScope.OWNER ? OWNER : null,
                new ReportPeriod(Periodicity.MONTHLY, period));
    }

    private static List<FinancialRecord> october() {
        Instant first = Instant.parse("2026-10-05T10:00:00Z");
        return List.of(
                record(FinancialRecordType.CHARGE, "1000.00", first),
                record(FinancialRecordType.REFUND, "100.00", first.plusSeconds(60)),
                record(FinancialRecordType.SETTLEMENT, "800.00", first.plusSeconds(120)),
                record(FinancialRecordType.COMMISSION, "10.00", first.plusSeconds(180)));
    }

    @Test
    void ce001_scope_platform_and_owner() {
        List<FinancialRecord> records = new ArrayList<>(october());
        FinancialReportService service = service(records);

        FinancialReportResult platform = service.get(command(ReportScope.PLATFORM, "2026-10"));
        FinancialReportResult owner = service.get(command(ReportScope.OWNER, "2026-10"));

        assertThat(platform.netAmount().amount()).isEqualByComparingTo("100");
        assertThat(owner.netAmount().amount()).isEqualByComparingTo("800");
        assertThat(owner.scope()).isEqualTo(ReportScope.OWNER);
    }

    @Test
    void ce002_net_without_double_commission() {
        FinancialReportResult result = service(october()).get(command(ReportScope.PLATFORM, "2026-10"));

        assertThat(result.totalCommissions().amount()).isEqualByComparingTo("10");
        assertThat(result.netAmount().amount()).isEqualByComparingTo("100");
    }

    @Test
    void ce003_fixed_period_boundaries() {
        ReportPeriodResolver resolver = new ReportPeriodResolver(() -> TODAY);

        assertThat(resolver.bounds(new ReportPeriod(Periodicity.SEMI_MONTHLY, "2026-10-H1")).endExclusive())
                .isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(resolver.bounds(new ReportPeriod(Periodicity.SEMI_MONTHLY, "2026-10-H2")).endExclusive())
                .isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void ce004_previous_equivalent_comparison() {
        Instant previous = Instant.parse("2026-09-05T10:00:00Z");
        List<FinancialRecord> records = new ArrayList<>(october());
        records.add(record(FinancialRecordType.CHARGE, "80.00", previous));

        FinancialReportResult result = service(records).get(command(ReportScope.PLATFORM, "2026-10"));

        assertThat(result.comparison().previousPeriod().value()).isEqualTo("2026-09");
        assertThat(result.comparison().absoluteVariation().amount()).isEqualByComparingTo("20");
        assertThat(result.comparison().percentageVariation()).isEqualByComparingTo("25");
    }

    @Test
    void ce005_pending_deposits_excluded() {
        FinancialReportResult result = service(october()).get(command(ReportScope.PLATFORM, "2026-10"));

        assertThat(result.counts()).containsOnlyKeys("CHARGE", "REFUND", "SETTLEMENT", "COMMISSION");
        assertThat(result.counts().values()).containsOnly(1);
    }

    @Test
    void ce006_csv_matches_json_report() {
        FinancialReportService service = service(october());
        FinancialReportResult report = service.get(command(ReportScope.PLATFORM, "2026-10"));
        FinancialReportExportResult export = service.export(command(ReportScope.PLATFORM, "2026-10"));

        String csv = new CsvReportExporter(new FinancialReportResponseMapper()).export(export);

        assertThat(csv).contains(report.netAmount().toOutputAmount().toPlainString());
        assertThat(csv).contains(report.comparison().absoluteVariation().toOutputAmount().toPlainString());
        assertThat(csv).contains("Fecha de Generacion,Alcance,Periodicidad,Periodo");
    }

    /** Fuente en memoria: agrega con el mismo criterio de alcance y ventana de fechas. */
    private static final class InMemoryQueryPort implements FinancialReportQueryPort {

        private final List<FinancialRecord> records;
        private final ReportPeriodResolver resolver = new ReportPeriodResolver(() -> TODAY);

        private InMemoryQueryPort(List<FinancialRecord> records) {
            this.records = List.copyOf(records);
        }

        @Override
        public ReportMetrics aggregate(GetFinancialReportCommand command) {
            ReportPeriodResolver.PeriodBounds bounds = resolver.bounds(command.period());
            Instant start = bounds.start().atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant end = bounds.endExclusive().atStartOfDay(ZoneOffset.UTC).toInstant();
            List<FinancialRecord> window = records.stream()
                    .filter(record -> !record.createdAt().isBefore(start) && record.createdAt().isBefore(end))
                    .filter(record -> command.scope() != ReportScope.OWNER || record.ownerId().equals(command.ownerId()))
                    .toList();
            return new FinancialAggregator().aggregate(window, command.scope());
        }
    }
}
