package com.seashare.seasharem3.infrastructure.adapter.out.persistence.financialreport;

import com.seashare.seasharem3.TestcontainersConfiguration;
import com.seashare.seasharem3.application.dto.GetFinancialReportCommand;
import com.seashare.seasharem3.domain.model.ReportMetrics;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.Periodicity;
import com.seashare.seasharem3.domain.valueobject.ReportPeriod;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integración real de la agregación SQL del informe financiero (UC13 T012-T016) contra PostgreSQL:
 * totales por tipo, neto de plataforma y de propietario, y exclusión de los registros ajenos al
 * alcance declarado.
 *
 * <p>Cada prueba usa un mes propio para no depender de otras filas del contenedor: los registros son
 * inmutables y no se pueden limpiar. Requiere Docker; en local se omite.</p>
 */
@SpringBootTest(properties = {IntegrationTestProperties.OUTBOX_RELAY_INTERVAL,
        IntegrationTestProperties.RETRY_INITIAL_INTERVAL, IntegrationTestProperties.RETRY_MULTIPLIER,
        IntegrationTestProperties.RETRY_MAX_INTERVAL})
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class FinancialReportQueryAdapterIntegrationTest {

    @Autowired
    private FinancialReportQueryAdapter adapter;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID reservationId;
    private UUID otherReservationId;
    private UUID chargeIntentId;
    private UUID boatId;
    private UUID ownerId;
    private UUID otherOwnerId;

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
        BigDecimal numericAmount = new BigDecimal(amount);
        jdbc.update("INSERT INTO charge_record (id, charge_intent_id, reservation_id, owner_id, boat_id, amount,"
                        + " rental_amount, insurance_amount, deposit_amount, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0, ?)",
                UUID.randomUUID(), chargeIntentId, reservation, owner, boatId, numericAmount, numericAmount,
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

    private void insertSettlementAndCommission(UUID reservation, UUID owner, String amount, Instant createdAt) {
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
                UUID.randomUUID(), recordId, intentId, reservation, owner, boatId, Timestamp.from(createdAt));
    }

    private static GetFinancialReportCommand command(ReportScope scope, OwnerId owner, String period) {
        return new GetFinancialReportCommand(Periodicity.MONTHLY, scope, owner,
                new ReportPeriod(Periodicity.MONTHLY, period));
    }

    @Test
    void aggregatesThePlatformReportFromTheFourImmutableTables() {
        Instant base = Instant.parse("2030-05-10T10:00:00Z");
        insertChargeRecord(reservationId, ownerId, "1000", base);
        insertRefundRecord(reservationId, ownerId, "100", base.plusSeconds(60));
        insertSettlementAndCommission(reservationId, ownerId, "800", base.plusSeconds(120));

        ReportMetrics metrics = adapter.aggregate(command(ReportScope.PLATFORM, null, "2030-05"));

        assertThat(metrics.totalCollections().amount()).isEqualByComparingTo("1000");
        assertThat(metrics.totalRefunds().amount()).isEqualByComparingTo("100");
        assertThat(metrics.totalSettlements().amount()).isEqualByComparingTo("800");
        assertThat(metrics.totalCommissions().amount()).isEqualByComparingTo("10");
        assertThat(metrics.netAmount().amount()).isEqualByComparingTo("100");
        assertThat(metrics.counts()).containsEntry("CHARGE", 1).containsEntry("COMMISSION", 1);
    }

    @Test
    void aggregatesTheOwnerReportOnlyWithTheOwnersRecords() {
        Instant base = Instant.parse("2030-06-10T10:00:00Z");
        insertSettlementAndCommission(reservationId, ownerId, "700", base);
        insertSettlementAndCommission(otherReservationId, otherOwnerId, "900", base.plusSeconds(60));

        ReportMetrics metrics = adapter.aggregate(
                command(ReportScope.OWNER, new OwnerId(ownerId), "2030-06"));

        assertThat(metrics.totalSettlements().amount()).isEqualByComparingTo("700");
        assertThat(metrics.netAmount().amount()).isEqualByComparingTo("700");
    }

    @Test
    void returnsZeroedMetricsForAPeriodWithoutRecords() {
        ReportMetrics metrics = adapter.aggregate(command(ReportScope.PLATFORM, null, "2030-07"));

        assertThat(metrics.netAmount().isZero()).isTrue();
        assertThat(metrics.counts()).containsEntry("CHARGE", 0).containsEntry("REFUND", 0);
    }
}
