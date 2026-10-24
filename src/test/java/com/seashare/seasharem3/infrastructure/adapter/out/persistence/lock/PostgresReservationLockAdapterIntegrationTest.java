package com.seashare.seasharem3.infrastructure.adapter.out.persistence.lock;

import com.seashare.seasharem3.TestcontainersConfiguration;
import com.seashare.seasharem3.application.port.out.ReservationLockPort;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.support.IntegrationTestProperties;
import com.seashare.seasharem3.support.RetryProbeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integración del bloqueo asesor de PostgreSQL ({@code pg_advisory_xact_lock}, D-15) contra una base real:
 * el bloqueo se mantiene dentro de la transacción del llamador y se libera al confirmarla, y reservas
 * distintas usan claves distintas. Requiere Docker; en local se omiten.
 */
@SpringBootTest(properties = {IntegrationTestProperties.OUTBOX_RELAY_INTERVAL,
        IntegrationTestProperties.RETRY_INITIAL_INTERVAL, IntegrationTestProperties.RETRY_MULTIPLIER,
        IntegrationTestProperties.RETRY_MAX_INTERVAL})
@Import({TestcontainersConfiguration.class, RetryProbeConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
class PostgresReservationLockAdapterIntegrationTest {

    @Autowired
    private ReservationLockPort lockPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void keepsTheAdvisoryLockInsideTheTransactionAndReleasesItOnCommit() {
        ReservationId reservationId = new ReservationId(UUID.randomUUID());

        Long locksInsideTransaction = transactionTemplate.execute(status -> {
            lockPort.lock(reservationId);
            return countAdvisoryLocks(reservationId);
        });

        assertThat(locksInsideTransaction).isEqualTo(1L);
        assertThat(countAdvisoryLocks(reservationId)).isZero();
    }

    @Test
    void differentReservationsUseDifferentLockKeys() {
        ReservationId locked = new ReservationId(UUID.randomUUID());
        ReservationId other = new ReservationId(UUID.randomUUID());

        transactionTemplate.executeWithoutResult(status -> {
            lockPort.lock(locked);
            assertThat(countAdvisoryLocks(locked)).isEqualTo(1L);
            assertThat(countAdvisoryLocks(other)).isZero();
        });
    }

    private long countAdvisoryLocks(ReservationId reservationId) {
        UUID value = reservationId.value();
        Long count = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM pg_locks
                WHERE locktype = 'advisory' AND objsubid = 2 AND pid = pg_backend_pid()
                  AND classid::bigint = ? AND objid::bigint = ?
                """, Long.class, value.getMostSignificantBits() & 0xFFFFFFFFL,
                value.getLeastSignificantBits() & 0xFFFFFFFFL);
        return count != null ? count : 0L;
    }
}
