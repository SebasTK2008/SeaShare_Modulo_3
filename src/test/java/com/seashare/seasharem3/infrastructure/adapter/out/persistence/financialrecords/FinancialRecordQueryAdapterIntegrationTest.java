package com.seashare.seasharem3.infrastructure.adapter.out.persistence.financialrecords;

import com.seashare.seasharem3.TestcontainersConfiguration;
import com.seashare.seasharem3.application.dto.FinancialRecordsPageResult;
import com.seashare.seasharem3.application.dto.QueryFinancialRecordsCommand;
import com.seashare.seasharem3.domain.valueobject.FinancialRecordType;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.ReportScope;
import com.seashare.seasharem3.support.IntegrationTestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integración real del adaptador de consulta de registros financieros (UC12 T016) contra PostgreSQL:
 * los cuatro tipos de registro, el filtro por propietario y la paginación estable
 * ({@code created_at DESC, record_id DESC}) sobre {@code v_financial_records}.
 *
 * <p>Cada prueba usa una ventana de fechas propia para no depender del orden de ejecución, porque
 * las tablas de registros son inmutables y no se pueden limpiar. Requiere Docker; en local se omite.</p>
 */
@SpringBootTest(properties = {IntegrationTestProperties.OUTBOX_RELAY_INTERVAL,
        IntegrationTestProperties.RETRY_INITIAL_INTERVAL, IntegrationTestProperties.RETRY_MULTIPLIER,
        IntegrationTestProperties.RETRY_MAX_INTERVAL})
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class FinancialRecordQueryAdapterIntegrationTest {

    @Autowired
    private FinancialRecordQueryAdapter adapter;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID reservationId;
    private UUID otherReservationId;
    private UUID chargeIntentId;
    private UUID ownerId;
    private UUID otherOwnerId;
    private UUID boatId;

    @BeforeEach
    void seed() {
        ownerId = UUID.randomUUID();
        otherOwnerId = UUID.randomUUID();
        boatId = UUID.randomUUID();
        reservationId = insertReservation(ownerId);
        otherReservationId = insertReservation(otherOwnerId);
        chargeIntentId = insertChargeIntent(reservationId);
    }

    private UUID insertReservation(UUID owner) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO reservation_information (reservation_id, boat_id, base_rate, start_date,"
                        + " end_date, passengers, owner_id, max_capacity)"
                        + " VALUES (?, ?, 100, DATE '2026-10-01', DATE '2026-10-10', 2, ?, 4)",
                id, UUID.randomUUID(), owner);
        return id;
    }

    private UUID insertChargeIntent(UUID reservation) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO charge_intent (id, reservation_id, idempotency_key, status, amount,"
                        + " payment_token_ref) VALUES (?, ?, ?, 'CAPTURED', 100, 'tok')",
                id, reservation, UUID.randomUUID().toString());
        return id;
    }

    private void insertChargeRecord(UUID reservation, UUID owner, String amount, Instant createdAt) {
        UUID intent = insertChargeIntent(reservation);
        BigDecimal numericAmount = new BigDecimal(amount);
        jdbc.update("INSERT INTO charge_record (id, charge_intent_id, reservation_id, owner_id, boat_id, amount,"
                        + " rental_amount, insurance_amount, deposit_amount, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0, ?)",
                UUID.randomUUID(), intent, reservation, owner, boatId, numericAmount, numericAmount,
                Timestamp.from(createdAt));
    }

    private void insertRefundRecord(UUID reservation, UUID owner, String amount, Instant createdAt) {
        UUID intentId = UUID.randomUUID();
        jdbc.update("INSERT INTO refund_intent (id, reservation_id, trigger, scope, amount, charge_intent_id,"
                        + " idempotency_key, status) VALUES (?, ?, 'CANCELLED_MODERATE', 'FULL', ?, ?, ?, 'COMPLETED')",
                intentId, reservation, new BigDecimal(amount), chargeIntentId, UUID.randomUUID().toString());
        BigDecimal numericAmount = new BigDecimal(amount);
        jdbc.update("INSERT INTO refund_record (id, refund_intent_id, reservation_id, owner_id, boat_id, amount,"
                        + " released_amount, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), intentId, reservation, owner, boatId, numericAmount, numericAmount,
                Timestamp.from(createdAt));
    }

    private void insertSettlementRecord(UUID reservation, UUID owner, String amount, Instant createdAt) {
        UUID intentId = UUID.randomUUID();
        jdbc.update("INSERT INTO settlement_intent (id, reservation_id, trigger, scope, amount, commission_amount,"
                        + " deposit_amount, charge_intent_id, idempotency_key, status, gateway_capability)"
                        + " VALUES (?, ?, 'COMPLETED', 'STANDARD', ?, 10, 0, ?, ?, 'COMPLETED',"
                        + " 'STANDARD_SETTLEMENT')",
                intentId, reservation, new BigDecimal(amount), chargeIntentId, UUID.randomUUID().toString());
        UUID recordId = UUID.randomUUID();
        BigDecimal numericAmount = new BigDecimal(amount);
        jdbc.update("INSERT INTO settlement_record (id, settlement_intent_id, reservation_id, owner_id, boat_id,"
                        + " amount, rental_gross_amount, insurance_amount, deposit_amount, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0, ?)",
                recordId, intentId, reservation, owner, boatId, numericAmount, numericAmount,
                Timestamp.from(createdAt));
        jdbc.update("INSERT INTO commission_record (id, settlement_record_id, settlement_intent_id, reservation_id,"
                        + " owner_id, boat_id, amount, created_at) VALUES (?, ?, ?, ?, ?, ?, 10, ?)",
                UUID.randomUUID(), recordId, intentId, reservation, owner, boatId,
                Timestamp.from(createdAt));
    }

    private QueryFinancialRecordsCommand command(ReportScope scope, OwnerId owner, int page, int size,
            LocalDate from) {
        return new QueryFinancialRecordsCommand(scope, page, size, null, owner, null, null, from, from.plusMonths(1));
    }

    @Test
    void returnsTheFourRecordTypesForTheDeclaredScope() {
        LocalDate window = LocalDate.of(2030, 1, 1);
        Instant base = Instant.parse("2030-01-10T10:00:00Z");
        insertChargeRecord(reservationId, ownerId, "1000", base);
        insertRefundRecord(reservationId, ownerId, "100", base.plusSeconds(60));
        insertSettlementRecord(reservationId, ownerId, "800", base.plusSeconds(120));

        FinancialRecordsPageResult page = adapter.query(
                command(ReportScope.OWNER, new OwnerId(ownerId), 1, 10, window));

        assertThat(page.totalElements()).isEqualTo(4);
        assertThat(page.content()).extracting(result -> result.recordType())
                .contains(FinancialRecordType.CHARGE, FinancialRecordType.REFUND,
                        FinancialRecordType.SETTLEMENT, FinancialRecordType.COMMISSION);
    }

    @Test
    void filtersByOwnerAndNeverLeaksAnotherOwnersRecords() {
        LocalDate window = LocalDate.of(2030, 2, 1);
        Instant base = Instant.parse("2030-02-10T10:00:00Z");
        insertChargeRecord(reservationId, ownerId, "1000", base);
        insertChargeRecord(otherReservationId, otherOwnerId, "2000", base.plusSeconds(60));

        FinancialRecordsPageResult page = adapter.query(
                command(ReportScope.OWNER, new OwnerId(ownerId), 1, 10, window));

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).ownerId().value()).isEqualTo(ownerId);
    }

    @Test
    void paginatesWithAStableOrder() {
        LocalDate window = LocalDate.of(2030, 3, 1);
        Instant base = Instant.parse("2030-03-10T10:00:00Z");
        insertChargeRecord(reservationId, ownerId, "100", base);
        insertChargeRecord(reservationId, ownerId, "200", base.plusSeconds(60));
        insertChargeRecord(reservationId, ownerId, "300", base.plusSeconds(120));

        FinancialRecordsPageResult first = adapter.query(
                command(ReportScope.PLATFORM, null, 1, 2, window));
        FinancialRecordsPageResult second = adapter.query(
                command(ReportScope.PLATFORM, null, 2, 2, window));

        assertThat(first.content()).hasSize(2);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(second.content()).hasSize(1);
        assertThat(first.content()).extracting(result -> result.amount().amount())
                .containsExactly(new BigDecimal("300.0000"), new BigDecimal("200.0000"));
    }
}
