package com.seashare.seasharem3.infrastructure.adapter.out.persistence.financialreport;

import com.seashare.seasharem3.application.dto.GetFinancialReportCommand;
import com.seashare.seasharem3.application.port.out.FinancialReportQueryPort;
import com.seashare.seasharem3.domain.model.FinancialRecord;
import com.seashare.seasharem3.domain.service.ReportPeriodResolver;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.FinancialRecordType;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.ReportScope;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Component
public class FinancialReportQueryAdapter implements FinancialReportQueryPort {

    private final NamedParameterJdbcTemplate jdbc;
    private final ZoneId businessZoneId;

    public FinancialReportQueryAdapter(NamedParameterJdbcTemplate jdbc, ZoneId businessZoneId) {
        this.jdbc = jdbc;
        this.businessZoneId = businessZoneId;
    }

    @Override
    public List<FinancialRecord> query(GetFinancialReportCommand command) {
        ReportPeriodResolver resolver = new ReportPeriodResolver(
                () -> LocalDate.now(businessZoneId));
        ReportPeriodResolver.PeriodBounds bounds = resolver.bounds(command.period());
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("start", bounds.start().atStartOfDay(businessZoneId).toInstant())
                .addValue("end", bounds.endExclusive().atStartOfDay(businessZoneId).toInstant());
        String scope = command.scope() == ReportScope.OWNER
                ? " AND owner_id = :ownerId" : "";
        if (command.ownerId() != null) parameters.addValue("ownerId", command.ownerId().value());
        String sql = """
                SELECT record_id, record_type, reservation_id, owner_id, boat_id, amount, created_at,
                       external_reference
                FROM v_financial_records
                WHERE created_at >= :start AND created_at < :end
                """ + scope + " ORDER BY created_at DESC, record_id DESC";
        return jdbc.query(sql, parameters, (rs, rowNum) -> new FinancialRecord(
                rs.getObject("record_id", UUID.class),
                FinancialRecordType.valueOf(rs.getString("record_type")),
                new ReservationId(rs.getObject("reservation_id", UUID.class)),
                new OwnerId(rs.getObject("owner_id", UUID.class)),
                new BoatId(rs.getObject("boat_id", UUID.class)),
                new Money(rs.getBigDecimal("amount")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getString("external_reference")));
    }
}
