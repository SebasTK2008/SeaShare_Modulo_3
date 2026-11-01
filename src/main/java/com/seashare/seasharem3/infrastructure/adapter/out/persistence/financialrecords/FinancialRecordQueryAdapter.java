package com.seashare.seasharem3.infrastructure.adapter.out.persistence.financialrecords;

import com.seashare.seasharem3.application.dto.FinancialRecordResult;
import com.seashare.seasharem3.application.dto.FinancialRecordsPageResult;
import com.seashare.seasharem3.application.dto.QueryFinancialRecordsCommand;
import com.seashare.seasharem3.application.port.out.FinancialRecordQueryPort;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.FinancialRecordType;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.ReportScope;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class FinancialRecordQueryAdapter implements FinancialRecordQueryPort {

    private static final String BASE_QUERY = """
            FROM v_financial_records
            WHERE 1 = 1
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ZoneId businessZoneId;

    public FinancialRecordQueryAdapter(NamedParameterJdbcTemplate jdbc, ZoneId businessZoneId) {
        this.jdbc = jdbc;
        this.businessZoneId = businessZoneId;
    }

    @Override
    public FinancialRecordsPageResult query(QueryFinancialRecordsCommand command) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        String predicates = predicates(command, parameters);
        String countSql = "SELECT COUNT(*) " + BASE_QUERY + predicates;
        long total = jdbc.queryForObject(countSql, parameters, Long.class);

        int pageSize = command.size();
        long totalPages = total == 0 ? 0 : ((total - 1) / pageSize) + 1;
        if ((long) command.page() > Math.max(totalPages, 1)) {
            return new FinancialRecordsPageResult(List.of(), command.page(), pageSize, total,
                    Math.toIntExact(totalPages));
        }
        long offset = ((long) command.page() - 1) * pageSize;
        String dataSql = """
                SELECT record_type, reservation_id, owner_id, boat_id, amount, created_at, external_reference
                """ + BASE_QUERY + predicates + """
                ORDER BY created_at DESC, record_id DESC
                LIMIT :limit OFFSET :offset
                """;
        parameters.addValue("limit", pageSize).addValue("offset", offset);
        List<FinancialRecordResult> content = jdbc.query(dataSql, parameters, (rs, rowNum) ->
                new FinancialRecordResult(
                        FinancialRecordType.valueOf(rs.getString("record_type")),
                        new ReservationId(rs.getObject("reservation_id", UUID.class)),
                        new OwnerId(rs.getObject("owner_id", UUID.class)),
                        new BoatId(rs.getObject("boat_id", UUID.class)),
                        new Money(rs.getBigDecimal("amount")),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getString("external_reference")));
        return new FinancialRecordsPageResult(content, command.page(), pageSize, total,
                Math.toIntExact(totalPages));
    }

    private String predicates(QueryFinancialRecordsCommand command, MapSqlParameterSource parameters) {
        List<String> conditions = new ArrayList<>();
        if (command.scope() == ReportScope.OWNER) {
            conditions.add("owner_id = :scopeOwnerId");
            parameters.addValue("scopeOwnerId", command.ownerId().value());
        } else if (command.ownerId() != null) {
            conditions.add("owner_id = :ownerId");
            parameters.addValue("ownerId", command.ownerId().value());
        }
        if (command.transactionType() != null) {
            conditions.add("record_type = :recordType");
            parameters.addValue("recordType", command.transactionType().name());
        }
        if (command.boatId() != null) {
            conditions.add("boat_id = :boatId");
            parameters.addValue("boatId", command.boatId().value());
        }
        if (command.reservationId() != null) {
            conditions.add("reservation_id = :reservationId");
            parameters.addValue("reservationId", command.reservationId().value());
        }
        if (command.dateFrom() != null) {
            conditions.add("created_at >= :dateFrom");
            parameters.addValue("dateFrom", startOfDay(command.dateFrom()));
        }
        if (command.dateTo() != null) {
            conditions.add("created_at < :dateToExclusive");
            parameters.addValue("dateToExclusive", startOfDay(command.dateTo().plusDays(1)));
        }
        return conditions.isEmpty() ? "" : " AND " + String.join(" AND ", conditions);
    }

    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(businessZoneId).toInstant();
    }
}
