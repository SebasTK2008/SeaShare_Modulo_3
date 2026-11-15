package com.seashare.seasharem3.infrastructure.adapter.out.persistence.failure;

import com.seashare.seasharem3.TestcontainersConfiguration;
import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.support.IntegrationTestProperties;
import com.seashare.seasharem3.support.RetryProbeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integración del registro de fallos en {@code operational_failure} contra PostgreSQL real: la
 * transacción independiente (REQUIRES_NEW) sobrevive al rollback de la transacción del llamador
 * (contracts/README §4.4). Requiere Docker; en local se omiten.
 */
@SpringBootTest(properties = {IntegrationTestProperties.OUTBOX_RELAY_INTERVAL,
        IntegrationTestProperties.RETRY_INITIAL_INTERVAL, IntegrationTestProperties.RETRY_MULTIPLIER,
        IntegrationTestProperties.RETRY_MAX_INTERVAL})
@Import({TestcontainersConfiguration.class, RetryProbeConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
class OperationalFailurePersistenceAdapterIntegrationTest {

    @Autowired
    private FailureRecorderPort failureRecorder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void recordSurvivesTheRollbackOfTheCallerTransaction() {
        UUID reservationId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status -> {
            failureRecorder.record("UC05", reservationId, "gateway timeout", "queue=finance.charge-command.v1");
            status.setRollbackOnly();
        });

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT use_case, reason, payload_ref, resolved FROM operational_failure
                WHERE reservation_id = ?
                """, reservationId);
        assertThat(row)
                .containsEntry("use_case", "UC05")
                .containsEntry("reason", "gateway timeout")
                .containsEntry("payload_ref", "queue=finance.charge-command.v1")
                .containsEntry("resolved", false);
    }

    @Test
    void recordAcceptsAnUnknownReservation() {
        String reason = "unknown reservation " + UUID.randomUUID();

        failureRecorder.record("UC10", null, reason, "queue=finance.settlement-command.v1");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM operational_failure WHERE reservation_id IS NULL AND reason = ?",
                Long.class, reason)).isEqualTo(1L);
    }
}
