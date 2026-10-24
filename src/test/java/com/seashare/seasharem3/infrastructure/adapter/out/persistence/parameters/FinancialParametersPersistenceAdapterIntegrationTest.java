package com.seashare.seasharem3.infrastructure.adapter.out.persistence.parameters;

import com.seashare.seasharem3.TestcontainersConfiguration;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import com.seashare.seasharem3.support.IntegrationTestProperties;
import com.seashare.seasharem3.support.RetryProbeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integración del adaptador append-only de {@code financial_parameters} contra PostgreSQL real:
 * la vigente es la de mayor {@code version_number} y {@code findById} resuelve la versión referenciada
 * por {@code frozen_parameters_id} (D-27). Requiere Docker; en local se omiten.
 */
@SpringBootTest(properties = {IntegrationTestProperties.OUTBOX_RELAY_INTERVAL,
        IntegrationTestProperties.RETRY_INITIAL_INTERVAL, IntegrationTestProperties.RETRY_MULTIPLIER,
        IntegrationTestProperties.RETRY_MAX_INTERVAL})
@Import({TestcontainersConfiguration.class, RetryProbeConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
class FinancialParametersPersistenceAdapterIntegrationTest {

    @Autowired
    private FinancialParametersPersistenceAdapter adapter;

    @Test
    void findCurrentReturnsTheVersionWithTheHighestVersionNumber() {
        FinancialParameters first = newVersion();

        FinancialParameters current = newVersion();

        FinancialParameters found = adapter.findCurrent().orElseThrow();
        assertThat(found.id()).isEqualTo(current.id());
        assertThat(found.versionNumber()).isEqualTo(first.versionNumber() + 1);
        assertThat(found.commissionPct()).isEqualTo(current.commissionPct());
        assertThat(found.insuranceFeePerPassenger()).isEqualTo(current.insuranceFeePerPassenger());
        assertThat(found.weekendIncreasePct()).isEqualTo(current.weekendIncreasePct());
        assertThat(found.highSeasonIncreasePct()).isEqualTo(current.highSeasonIncreasePct());
        assertThat(found.createdBy()).isEqualTo("integration-test");
        assertThat(found.createdAt()).isEqualTo(current.createdAt());
    }

    @Test
    void findByIdReturnsTheReferencedVersionEvenIfANewerOneExists() {
        FinancialParameters referenced = newVersion();
        newVersion();

        FinancialParameters found = adapter.findById(referenced.id()).orElseThrow();
        assertThat(found.versionNumber()).isEqualTo(referenced.versionNumber());
    }

    @Test
    void saveNewVersionRejectsAnExistingIdBecauseTheTableIsAppendOnly() {
        FinancialParameters version = newVersion();

        assertThatThrownBy(() -> adapter.saveNewVersion(version))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(version.id().toString());
    }

    private FinancialParameters newVersion() {
        long nextVersion = adapter.findCurrent().map(FinancialParameters::versionNumber).orElse(0L) + 1;
        return adapter.saveNewVersion(FinancialParameters.create(UUID.randomUUID(), nextVersion,
                new Percentage(new BigDecimal("10")), new Money(new BigDecimal("5000")),
                new Percentage(new BigDecimal("15")), new Percentage(new BigDecimal("20")), "integration-test",
                Instant.parse("2026-10-01T12:00:00Z")));
    }
}
