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

    public FinancialRecordQueryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public FinancialRecordsPageResult query(QueryFinancialRecordsCommand command) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        String predicates = predicates(command, parameters);
        String countSql = "SELECT COUNT(*) " + BASE_QUERY + predicates;
        long total = jdbc.queryForObject(countSql, parameters, Long.class);

        int pageSize = command.size();
        int offset = (command.page() - 1) * pageSize;
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
        int totalPages = total == 0 ? 0 : (int) ((total + pageSize - 1) / pageSize);
        return new FinancialRecordsPageResult(content, command.page(), pageSize, total, totalPages);
    }

    private static String predicates(QueryFinancialRecordsCommand command, MapSqlParameterSource parameters) {
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
            parameters.addValue("dateFrom", command.dateFrom());
        }
        if (command.dateTo() != null) {
            conditions.add("created_at < (:dateTo + INTERVAL '1 day')");
            parameters.addValue("dateTo", command.dateTo());
        }
        return conditions.isEmpty() ? "" : " AND " + String.join(" AND ", conditions);
    }
}
