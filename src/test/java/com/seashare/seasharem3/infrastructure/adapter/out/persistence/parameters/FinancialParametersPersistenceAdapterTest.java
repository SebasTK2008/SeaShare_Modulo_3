package com.seashare.seasharem3.infrastructure.adapter.out.persistence.parameters;

import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prueba unitaria del adaptador append-only: usa {@code Persistable} y no hace el
 * {@code existsById}/SELECT previo antes del INSERT (B6).
 */
class FinancialParametersPersistenceAdapterTest {

    private final FinancialParametersJpaRepository repository = mock(FinancialParametersJpaRepository.class);
    private final FinancialParametersPersistenceAdapter adapter =
            new FinancialParametersPersistenceAdapter(repository);

    @Test
    void saveNewVersionPersistsWithoutCheckingExistenceFirst() {
        FinancialParameters parameters = newVersion();
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        FinancialParameters saved = adapter.saveNewVersion(parameters);

        ArgumentCaptor<FinancialParametersJpaEntity> captor =
                ArgumentCaptor.forClass(FinancialParametersJpaEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        verify(repository, never()).existsById(any());
        assertThat(captor.getValue().isNew()).isTrue();
        assertThat(saved.id()).isEqualTo(parameters.id());
        assertThat(saved.versionNumber()).isEqualTo(parameters.versionNumber());
    }

    private static FinancialParameters newVersion() {
        return FinancialParameters.create(UUID.randomUUID(), 7,
                new Percentage(new BigDecimal("15.00")),
                new Money(new BigDecimal("15000.00")),
                new Percentage(new BigDecimal("10.00")),
                new Percentage(new BigDecimal("25.00")),
                null, Instant.parse("2026-10-24T12:00:00Z"));
    }
}
