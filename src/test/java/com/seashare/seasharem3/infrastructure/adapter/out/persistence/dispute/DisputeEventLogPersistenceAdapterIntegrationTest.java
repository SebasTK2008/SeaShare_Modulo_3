package com.seashare.seasharem3.infrastructure.adapter.out.persistence.dispute;

import com.seashare.seasharem3.TestcontainersConfiguration;
import com.seashare.seasharem3.domain.model.DisputeEvent;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.DisputeId;
import com.seashare.seasharem3.domain.valueobject.DisputeStatus;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.parameters.FinancialParametersPersistenceAdapter;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.reservation.ReservationInformationPersistenceAdapter;
import com.seashare.seasharem3.support.ChargeFixtures;
import com.seashare.seasharem3.support.IntegrationTestProperties;
import com.seashare.seasharem3.support.RetryProbeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integracion de {@code dispute_event_log} contra PostgreSQL real: reclamo idempotente por la clave
 * de negocio, lectura del evento, marca de procesado y concurrencia de la clave unica.
 */
@SpringBootTest(properties = {IntegrationTestProperties.OUTBOX_RELAY_INTERVAL,
        IntegrationTestProperties.RETRY_INITIAL_INTERVAL, IntegrationTestProperties.RETRY_MULTIPLIER,
        IntegrationTestProperties.RETRY_MAX_INTERVAL})
@Import({TestcontainersConfiguration.class, RetryProbeConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
class DisputeEventLogPersistenceAdapterIntegrationTest {

    private static final Instant STATUS_CHANGED_AT = Instant.parse("2026-12-23T10:00:00Z");
    private static final Instant PROCESSED_AT = Instant.parse("2026-12-23T10:00:01Z");

    @Autowired
    private DisputeEventLogPersistenceAdapter adapter;

    @Autowired
    private ReservationInformationPersistenceAdapter reservationAdapter;

    @Autowired
    private FinancialParametersPersistenceAdapter parametersAdapter;

    @Test
    void insertsOnlyOnceAndMarksTheEventAsProcessed() {
        ReservationId reservationId = createReservation();
        DisputeId disputeId = new DisputeId(UUID.randomUUID());
        DisputeEvent event = new DisputeEvent(UUID.randomUUID(), reservationId, disputeId, "evt-1",
                DisputeStatus.PENDING, STATUS_CHANGED_AT, null);

        assertThat(adapter.insertIfAbsent(event)).isTrue();
        assertThat(adapter.insertIfAbsent(event)).isFalse();
        assertThat(adapter.findByKey(reservationId, disputeId, "evt-1")).hasValueSatisfying(stored -> {
            assertThat(stored.id()).isEqualTo(event.id());
            assertThat(stored.status()).isEqualTo(DisputeStatus.PENDING);
            assertThat(stored.processedAt()).isNull();
        });

        assertThat(adapter.markProcessed(event.id(), PROCESSED_AT)).isTrue();
        assertThat(adapter.findByKey(reservationId, disputeId, "evt-1")).hasValueSatisfying(stored ->
                assertThat(stored.processedAt()).isEqualTo(PROCESSED_AT));
    }

    @Test
    void concurrentInsertsOfTheSameEventKeyOnlyOneWins() throws Exception {
        ReservationId reservationId = createReservation();
        DisputeId disputeId = new DisputeId(UUID.randomUUID());
        DisputeEvent first = new DisputeEvent(UUID.randomUUID(), reservationId, disputeId, "evt-concurrent",
                DisputeStatus.REJECTED, STATUS_CHANGED_AT, null);
        DisputeEvent second = new DisputeEvent(UUID.randomUUID(), reservationId, disputeId, "evt-concurrent",
                DisputeStatus.REJECTED, STATUS_CHANGED_AT, null);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Boolean> insertFirst = () -> {
                start.await();
                return adapter.insertIfAbsent(first);
            };
            Callable<Boolean> insertSecond = () -> {
                start.await();
                return adapter.insertIfAbsent(second);
            };
            Future<Boolean> firstResult = executor.submit(insertFirst);
            Future<Boolean> secondResult = executor.submit(insertSecond);
            start.countDown();

            assertThat(List.of(firstResult.get(30, TimeUnit.SECONDS), secondResult.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }
    }

    private ReservationId createReservation() {
        ReservationInformation info = ChargeFixtures.calculatedReservation();
        long version = parametersAdapter.findCurrent().map(parameters -> parameters.versionNumber() + 1).orElse(1L);
        FinancialParameters parameters = parametersAdapter.saveNewVersion(FinancialParameters.create(
                UUID.randomUUID(), version, new Percentage(new BigDecimal("20")), Money.ZERO, Percentage.ZERO,
                Percentage.ZERO, "test", ChargeFixtures.CREATED_AT));
        ReservationInformation saved = reservationAdapter.save(ReservationInformation.create(info.reservationId(),
                info.boatId(), info.baseRate(), parameters.id(), info.startDate(), info.endDate(),
                info.passengers(), info.ownerId(), info.maxCapacity()));
        return saved.reservationId();
    }
}
