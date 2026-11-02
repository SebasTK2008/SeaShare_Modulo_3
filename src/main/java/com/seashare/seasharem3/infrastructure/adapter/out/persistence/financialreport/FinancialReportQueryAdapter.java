package com.seashare.seasharem3.infrastructure.adapter.out.persistence.financialreport;

import com.seashare.seasharem3.application.dto.GetFinancialReportCommand;
import com.seashare.seasharem3.application.port.out.FinancialReportQueryPort;
import com.seashare.seasharem3.domain.model.ReportMetrics;
import com.seashare.seasharem3.application.port.out.ClockPort;
import com.seashare.seasharem3.domain.service.ReportPeriodResolver;
import com.seashare.seasharem3.domain.valueobject.FinancialRecordType;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.ReportScope;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agregación SQL sobre {@code v_financial_records} para el informe financiero (UC13).
 *
 * <p>Es la única ruta de cálculo del informe: el dominio no recalcula los totales que este
 * adaptador ya agrega en la base de datos.</p>
 */
@Component
public class FinancialReportQueryAdapter implements FinancialReportQueryPort {

    private final NamedParameterJdbcTemplate jdbc;
    private final ReportPeriodResolver periodResolver;
    private final ZoneId businessZoneId;

    public FinancialReportQueryAdapter(NamedParameterJdbcTemplate jdbc, ClockPort clock, ZoneId businessZoneId) {
        this.jdbc = jdbc;
        this.periodResolver = new ReportPeriodResolver(clock::today);
        this.businessZoneId = businessZoneId;
    }

    @Override
    public ReportMetrics aggregate(GetFinancialReportCommand command) {
        ReportPeriodResolver.PeriodBounds bounds = periodResolver.bounds(command.period());
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("start", bounds.start().atStartOfDay(businessZoneId).toOffsetDateTime())
                .addValue("end", bounds.endExclusive().atStartOfDay(businessZoneId).toOffsetDateTime());
        String scope = command.scope() == ReportScope.OWNER ? " AND owner_id = :ownerId" : "";
        if (command.ownerId() != null) {
            parameters.addValue("ownerId", command.ownerId().value());
        }
        String sql = """
                SELECT record_type, COUNT(*) AS record_count, COALESCE(SUM(amount), 0) AS total_amount
                FROM v_financial_records
                WHERE created_at >= :start AND created_at < :end
                """ + scope + " GROUP BY record_type";
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, Money> totals = new LinkedHashMap<>();
        for (FinancialRecordType type : FinancialRecordType.values()) {
            counts.put(type.name(), 0);
            totals.put(type.name(), Money.ZERO);
        }
        jdbc.query(sql, parameters, rs -> {
            String type = rs.getString("record_type");
            counts.put(type, rs.getInt("record_count"));
            totals.put(type, new Money(rs.getBigDecimal("total_amount")));
        });
        Money collections = totals.get(FinancialRecordType.CHARGE.name());
        Money refunds = totals.get(FinancialRecordType.REFUND.name());
        Money settlements = totals.get(FinancialRecordType.SETTLEMENT.name());
        Money commissions = totals.get(FinancialRecordType.COMMISSION.name());
        Money net = command.scope() == ReportScope.OWNER ? settlements
                : collections.subtract(refunds).subtract(settlements);
        return new ReportMetrics(net, collections, refunds, settlements, commissions, counts);
    }
}
