package com.seashare.seasharem3.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class FlywaySchemaTest {

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

	static Flyway flyway;

	@BeforeAll
	static void migrate() {
		flyway = Flyway.configure()
				.dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
				.locations("classpath:db/migration")
				.load();
		flyway.migrate();
	}

	@Test
	void appliesAllMigrationsInOrder() throws SQLException {
		List<String> versions = queryStrings(
				"SELECT version FROM flyway_schema_history WHERE success = TRUE ORDER BY installed_rank");
		assertThat(versions).containsExactly("1");
	}

	@Test
	void createsAllSharedTables() throws SQLException {
		List<String> tables = queryStrings(
				"SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name");
		assertThat(tables).contains(
				"charge_intent", "charge_record", "commission_record", "deposit_disposition",
				"dispute_event_log", "financial_parameters", "operational_failure", "outbox_message",
				"refund_intent", "refund_record", "reservation_information", "reservation_status_log",
				"settlement_intent", "settlement_record", "shedlock");
	}

	@Test
	void addsIntentTraceabilityColumns() throws SQLException {
		assertThat(columnNames("refund_intent")).contains("created_at");
		assertThat(columnNames("settlement_intent"))
				.contains("created_at", "external_reference", "status_detail", "dispute_id");
	}

	@Test
	void enforcesOneRecordPerIntent() throws SQLException {
		Fixture fixture = new Fixture();
		execute("INSERT INTO charge_record (id, charge_intent_id, reservation_id, owner_id, boat_id, amount,"
				+ " rental_amount, insurance_amount, deposit_amount)"
				+ " VALUES (?, ?, ?, ?, ?, 100, 60, 20, 20)",
				UUID.randomUUID(), fixture.chargeIntentId, fixture.reservationId, UUID.randomUUID(), UUID.randomUUID());
		assertThatThrownBy(() -> execute(
				"INSERT INTO charge_record (id, charge_intent_id, reservation_id, owner_id, boat_id, amount,"
						+ " rental_amount, insurance_amount, deposit_amount)"
						+ " VALUES (?, ?, ?, ?, ?, 100, 60, 20, 20)",
				UUID.randomUUID(), fixture.chargeIntentId, fixture.reservationId, UUID.randomUUID(), UUID.randomUUID()))
				.isInstanceOf(SQLException.class);

		Fixture refund = new Fixture();
		execute("INSERT INTO refund_record (id, refund_intent_id, reservation_id, owner_id, boat_id, amount, released_amount)"
				+ " VALUES (?, ?, ?, ?, ?, 50, 50)",
				UUID.randomUUID(), refund.refundIntentId, refund.reservationId, UUID.randomUUID(), UUID.randomUUID());
		assertThatThrownBy(() -> execute(
				"INSERT INTO refund_record (id, refund_intent_id, reservation_id, owner_id, boat_id, amount, released_amount)"
						+ " VALUES (?, ?, ?, ?, ?, 50, 50)",
				UUID.randomUUID(), refund.refundIntentId, refund.reservationId, UUID.randomUUID(), UUID.randomUUID()))
				.isInstanceOf(SQLException.class);

		Fixture settlement = new Fixture();
		execute("INSERT INTO settlement_record (id, settlement_intent_id, reservation_id, owner_id, boat_id,"
				+ " amount, rental_gross_amount, insurance_amount, deposit_amount)"
				+ " VALUES (?, ?, ?, ?, ?, 90, 60, 20, 10)",
				UUID.randomUUID(), settlement.settlementIntentId, settlement.reservationId, UUID.randomUUID(),
				UUID.randomUUID());
		assertThatThrownBy(() -> execute(
				"INSERT INTO settlement_record (id, settlement_intent_id, reservation_id, owner_id, boat_id,"
						+ " amount, rental_gross_amount, insurance_amount, deposit_amount)"
						+ " VALUES (?, ?, ?, ?, ?, 90, 60, 20, 10)",
				UUID.randomUUID(), settlement.settlementIntentId, settlement.reservationId, UUID.randomUUID(),
				UUID.randomUUID())).isInstanceOf(SQLException.class);
	}

	@Test
	void recordTablesAreImmutable() throws SQLException {
		Fixture charge = new Fixture();
		UUID chargeRecordId = UUID.randomUUID();
		execute("INSERT INTO charge_record (id, charge_intent_id, reservation_id, owner_id, boat_id, amount,"
				+ " rental_amount, insurance_amount, deposit_amount)"
				+ " VALUES (?, ?, ?, ?, ?, 100, 60, 20, 20)",
				chargeRecordId, charge.chargeIntentId, charge.reservationId, UUID.randomUUID(), UUID.randomUUID());

		Fixture refund = new Fixture();
		UUID refundRecordId = UUID.randomUUID();
		execute("INSERT INTO refund_record (id, refund_intent_id, reservation_id, owner_id, boat_id, amount, released_amount)"
				+ " VALUES (?, ?, ?, ?, ?, 50, 50)",
				refundRecordId, refund.refundIntentId, refund.reservationId, UUID.randomUUID(), UUID.randomUUID());

		Fixture settlement = new Fixture();
		UUID settlementRecordId = UUID.randomUUID();
		execute("INSERT INTO settlement_record (id, settlement_intent_id, reservation_id, owner_id, boat_id,"
				+ " amount, rental_gross_amount, insurance_amount, deposit_amount)"
				+ " VALUES (?, ?, ?, ?, ?, 90, 60, 20, 10)",
				settlementRecordId, settlement.settlementIntentId, settlement.reservationId, UUID.randomUUID(),
				UUID.randomUUID());
		UUID commissionRecordId = UUID.randomUUID();
		execute("INSERT INTO commission_record (id, settlement_record_id, settlement_intent_id, reservation_id,"
				+ " owner_id, boat_id, amount) VALUES (?, ?, ?, ?, ?, ?, 10)",
				commissionRecordId, settlementRecordId, settlement.settlementIntentId, settlement.reservationId,
				UUID.randomUUID(), UUID.randomUUID());

		assertRejectsMutation("charge_record", chargeRecordId);
		assertRejectsMutation("refund_record", refundRecordId);
		assertRejectsMutation("settlement_record", settlementRecordId);
		assertRejectsMutation("commission_record", commissionRecordId);

		assertRejectsTruncate("charge_record");
		assertRejectsTruncate("refund_record");
		assertRejectsTruncate("settlement_record");
		assertRejectsTruncate("commission_record");
	}

	@Test
	void financialParametersAreAppendOnly() throws SQLException {
		UUID parametersId = insertParameters(1000L);
		assertThatThrownBy(() -> execute(
				"UPDATE financial_parameters SET commission_pct = 1 WHERE id = ?", parametersId))
				.isInstanceOf(SQLException.class);
		assertThatThrownBy(() -> execute(
				"DELETE FROM financial_parameters WHERE id = ?", parametersId))
				.isInstanceOf(SQLException.class);
		assertRejectsTruncate("financial_parameters");
	}

	@Test
	void createsTruncateGuardsForImmutableTables() throws SQLException {
		List<String> triggers = queryStrings(
				"SELECT tgname FROM pg_trigger WHERE tgname LIKE '%no_truncate' ORDER BY tgname");
		assertThat(triggers).containsExactly(
				"trg_charge_record_no_truncate",
				"trg_commission_record_no_truncate",
				"trg_financial_parameters_no_truncate",
				"trg_refund_record_no_truncate",
				"trg_settlement_record_no_truncate");
	}

	@Test
	void enforcesFinancialParametersRangeChecks() {
		assertThatThrownBy(() -> insertParametersWithValues(3000L, "100.0001", "5", "15", "20"))
				.isInstanceOf(SQLException.class);
		assertThatThrownBy(() -> insertParametersWithValues(3001L, "-0.0001", "5", "15", "20"))
				.isInstanceOf(SQLException.class);
		assertThatThrownBy(() -> insertParametersWithValues(3002L, "10", "5", "100.0001", "20"))
				.isInstanceOf(SQLException.class);
		assertThatThrownBy(() -> insertParametersWithValues(3003L, "10", "5", "15", "100.0001"))
				.isInstanceOf(SQLException.class);
		assertThatThrownBy(() -> insertParametersWithValues(3004L, "10", "-0.0001", "15", "20"))
				.isInstanceOf(SQLException.class);
	}

	@Test
	void acceptsFinancialParametersBoundaries() {
		assertThatCode(() -> insertParametersWithValues(3010L, "0", "0", "0", "0"))
				.doesNotThrowAnyException();
		assertThatCode(() -> insertParametersWithValues(3011L, "100", "0", "100", "100"))
				.doesNotThrowAnyException();
	}

	@Test
	void enforcesUniqueFinancialParametersVersionNumber() throws SQLException {
		insertParameters(3020L);

		assertThatThrownBy(() -> insertParameters(3020L))
				.isInstanceOf(SQLException.class);
	}

	@Test
	void enforcesReservationInvariants() throws SQLException {
		UUID parametersId = insertParameters(1001L);
		assertThatThrownBy(() -> execute(
				"INSERT INTO reservation_information (reservation_id, boat_id, base_rate, start_date, end_date,"
						+ " passengers, owner_id, max_capacity, base_rate_parameters_id)"
						+ " VALUES (?, ?, 100, DATE '2026-10-10', DATE '2026-10-01', 2, ?, 4, ?)",
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), parametersId))
				.isInstanceOf(SQLException.class);
		assertThatThrownBy(() -> execute(
				"INSERT INTO reservation_information (reservation_id, boat_id, base_rate, start_date, end_date,"
						+ " passengers, owner_id, max_capacity, base_rate_parameters_id)"
						+ " VALUES (?, ?, 100, DATE '2026-10-01', DATE '2026-10-10', 9, ?, 4, ?)",
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), parametersId))
				.isInstanceOf(SQLException.class);
	}

	@Test
	void enforcesIntentIdempotencyKeyAndSettlementTraceability() throws SQLException {
		Fixture fixture = new Fixture();
		assertThatThrownBy(() -> execute(
				"INSERT INTO charge_intent (id, reservation_id, idempotency_key, status, amount, payment_token_ref)"
						+ " VALUES (?, ?, ?, 'PENDING_SEND', 100, 'tok')",
				UUID.randomUUID(), fixture.reservationId, fixture.chargeIdempotencyKey))
				.isInstanceOf(SQLException.class);

		UUID settlementIntentId = UUID.randomUUID();
		UUID disputeId = UUID.randomUUID();
		execute("INSERT INTO settlement_intent (id, reservation_id, trigger, scope, amount, commission_amount,"
				+ " deposit_amount, charge_intent_id, idempotency_key, status, status_detail, external_reference,"
				+ " dispute_id) VALUES (?, ?, 'DISPUTE_COMPLETED', 'DEPOSIT', 90, 10, 80, ?, ?, 'PENDING_SEND',"
				+ " 'detalle', ?, ?)",
				settlementIntentId, fixture.reservationId, fixture.chargeIntentId, UUID.randomUUID().toString(),
				"9001", disputeId);
		assertThat(queryStrings("SELECT external_reference FROM settlement_intent WHERE id = ?", settlementIntentId))
				.containsExactly("9001");
		assertThat(queryStrings("SELECT dispute_id::text FROM settlement_intent WHERE id = ?", settlementIntentId))
				.containsExactly(disputeId.toString());
	}

	@Test
	void viewUnionsTheFourRecordTables() throws SQLException {
		assertThat(columnNames("v_financial_records")).containsExactly(
				"record_type", "record_id", "reservation_id", "owner_id", "boat_id", "amount", "created_at",
				"external_reference");

		Fixture fixture = new Fixture();
		execute("INSERT INTO charge_record (id, charge_intent_id, reservation_id, owner_id, boat_id, amount,"
				+ " rental_amount, insurance_amount, deposit_amount)"
				+ " VALUES (?, ?, ?, ?, ?, 100, 60, 20, 20)",
				UUID.randomUUID(), fixture.chargeIntentId, fixture.reservationId, UUID.randomUUID(), UUID.randomUUID());
		execute("INSERT INTO refund_record (id, refund_intent_id, reservation_id, owner_id, boat_id, amount, released_amount)"
				+ " VALUES (?, ?, ?, ?, ?, 50, 50)",
				UUID.randomUUID(), fixture.refundIntentId, fixture.reservationId, UUID.randomUUID(), UUID.randomUUID());
		UUID settlementRecordId = UUID.randomUUID();
		execute("INSERT INTO settlement_record (id, settlement_intent_id, reservation_id, owner_id, boat_id,"
				+ " amount, rental_gross_amount, insurance_amount, deposit_amount)"
				+ " VALUES (?, ?, ?, ?, ?, 90, 60, 20, 10)",
				settlementRecordId, fixture.settlementIntentId, fixture.reservationId, UUID.randomUUID(),
				UUID.randomUUID());
		execute("INSERT INTO commission_record (id, settlement_record_id, settlement_intent_id, reservation_id,"
				+ " owner_id, boat_id, amount) VALUES (?, ?, ?, ?, ?, ?, 10)",
				UUID.randomUUID(), settlementRecordId, fixture.settlementIntentId, fixture.reservationId,
				UUID.randomUUID(), UUID.randomUUID());

		List<String> types = queryStrings(
				"SELECT record_type FROM v_financial_records WHERE reservation_id = ? ORDER BY record_type",
				fixture.reservationId);
		assertThat(types).containsExactly("CHARGE", "COMMISSION", "REFUND", "SETTLEMENT");
	}

	@Test
	void createsSupportIndexes() throws SQLException {
		List<String> indexes = queryStrings(
				"SELECT indexname FROM pg_indexes WHERE schemaname = 'public' ORDER BY indexname");
		assertThat(indexes).contains(
				"idx_charge_intent_external_reference",
				"idx_refund_intent_external_reference",
				"idx_settlement_intent_external_reference",
				"idx_operational_failure_unresolved",
				"idx_operational_failure_reservation",
				"idx_outbox_message_unpublished");
	}

	private static void assertRejectsMutation(String table, UUID id) {
		assertThatCode(() -> execute("UPDATE " + table + " SET amount = 1 WHERE id = ?", id))
				.isInstanceOf(SQLException.class);
		assertThatCode(() -> execute("DELETE FROM " + table + " WHERE id = ?", id))
				.isInstanceOf(SQLException.class);
	}

	private static void assertRejectsTruncate(String table) {
		assertThatCode(() -> execute("TRUNCATE TABLE " + table))
				.isInstanceOf(SQLException.class);
	}

	private static UUID insertParameters(long versionNumber) throws SQLException {
		UUID id = UUID.randomUUID();
		execute("INSERT INTO financial_parameters (id, version_number, commission_pct,"
				+ " insurance_fee_per_passenger, weekend_increase_pct, high_season_increase_pct)"
				+ " VALUES (?, ?, 10, 5, 15, 20)", id, versionNumber);
		return id;
	}

	private static void insertParametersWithValues(long versionNumber, String commission,
			String insurance, String weekend, String highSeason) throws SQLException {
		execute("INSERT INTO financial_parameters (id, version_number, commission_pct,"
				+ " insurance_fee_per_passenger, weekend_increase_pct, high_season_increase_pct)"
				+ " VALUES (?, ?, ?, ?, ?, ?)",
				UUID.randomUUID(), versionNumber, new BigDecimal(commission), new BigDecimal(insurance),
				new BigDecimal(weekend), new BigDecimal(highSeason));
	}

	private static void execute(String sql, Object... args) throws SQLException {
		try (Connection connection = DriverManager.getConnection(
				POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
				PreparedStatement statement = connection.prepareStatement(sql)) {
			for (int i = 0; i < args.length; i++) {
				statement.setObject(i + 1, args[i]);
			}
			statement.executeUpdate();
		}
	}

	private static List<String> queryStrings(String sql, Object... args) throws SQLException {
		try (Connection connection = DriverManager.getConnection(
				POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
				PreparedStatement statement = connection.prepareStatement(sql)) {
			for (int i = 0; i < args.length; i++) {
				statement.setObject(i + 1, args[i]);
			}
			try (ResultSet resultSet = statement.executeQuery()) {
				List<String> values = new ArrayList<>();
				while (resultSet.next()) {
					values.add(resultSet.getString(1));
				}
				return values;
			}
		}
	}

	private static List<String> columnNames(String tableName) throws SQLException {
		return queryStrings(
				"SELECT column_name FROM information_schema.columns WHERE table_schema = 'public'"
						+ " AND table_name = ? ORDER BY ordinal_position",
				tableName);
	}

	private static final class Fixture {

		final UUID reservationId = UUID.randomUUID();
		final UUID chargeIntentId = UUID.randomUUID();
		final String chargeIdempotencyKey = UUID.randomUUID().toString();
		final UUID refundIntentId = UUID.randomUUID();
		final UUID settlementIntentId = UUID.randomUUID();

		Fixture() throws SQLException {
			execute("INSERT INTO reservation_information (reservation_id, boat_id, base_rate, start_date,"
					+ " end_date, passengers, owner_id, max_capacity)"
					+ " VALUES (?, ?, 100, DATE '2026-10-01', DATE '2026-10-10', 2, ?, 4)",
					reservationId, UUID.randomUUID(), UUID.randomUUID());
			execute("INSERT INTO charge_intent (id, reservation_id, idempotency_key, status, amount,"
					+ " payment_token_ref) VALUES (?, ?, ?, 'CAPTURED', 100, 'tok')",
					chargeIntentId, reservationId, chargeIdempotencyKey);
			execute("INSERT INTO refund_intent (id, reservation_id, trigger, scope, amount, charge_intent_id,"
					+ " idempotency_key, status) VALUES (?, ?, 'CANCELLED_MODERATE', 'FULL', 100, ?, ?,"
					+ " 'CONFIRMED')",
					refundIntentId, reservationId, chargeIntentId, UUID.randomUUID().toString());
			execute("INSERT INTO settlement_intent (id, reservation_id, trigger, scope, amount,"
					+ " commission_amount, deposit_amount, charge_intent_id, idempotency_key, status)"
					+ " VALUES (?, ?, 'COMPLETED', 'STANDARD', 90, 10, 10, ?, ?, 'CONFIRMED')",
					settlementIntentId, reservationId, chargeIntentId, UUID.randomUUID().toString());
		}

	}

}
