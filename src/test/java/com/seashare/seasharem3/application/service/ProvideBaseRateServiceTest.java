package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.BaseRateResult;
import com.seashare.seasharem3.application.dto.ProvideBaseRateCommand;
import com.seashare.seasharem3.application.port.out.FinancialParametersRepository;
import com.seashare.seasharem3.application.port.out.FleetRatePort;
import com.seashare.seasharem3.domain.exception.BaseRateException;
import com.seashare.seasharem3.domain.exception.BaseRateException.BaseRateFailureReason;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.service.DynamicRatePolicy;
import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import com.seashare.seasharem3.support.InMemoryFailureRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProvideBaseRateServiceTest {

    private static final LocalDate REGULAR_DATE = LocalDate.of(2026, 9, 30);
    private static final LocalDate WEEKEND_DATE = LocalDate.of(2026, 10, 3);
    private static final BoatId BOAT_A = new BoatId(UUID.fromString("3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01"));
    private static final BoatId BOAT_B = new BoatId(UUID.fromString("4d7a9c10-5a2b-4e6f-8d01-3f2c1a548b3e"));
    private static final BaseRate BASE_RATE = new BaseRate(new Money(new BigDecimal("350000.00")));

    @Mock
    private FleetRatePort fleetRatePort;

    @Mock
    private FinancialParametersRepository financialParametersRepository;

    @Mock
    private DynamicRatePolicy dynamicRatePolicy;

    private InMemoryFailureRecorder failureRecorder;
    private ProvideBaseRateService service;

    @BeforeEach
    void setUp() {
        failureRecorder = new InMemoryFailureRecorder();
        service = new ProvideBaseRateService(fleetRatePort, financialParametersRepository,
                dynamicRatePolicy, failureRecorder);
    }

    @Test
    void returnsEmptyWithoutCallingDependenciesWhenThereAreNoBoats() {
        List<BaseRateResult> result = service.provideBaseRates(
                new ProvideBaseRateCommand(List.of(), REGULAR_DATE));

        assertThat(result).isEmpty();
        verifyNoInteractions(fleetRatePort, financialParametersRepository, dynamicRatePolicy);
    }

    @Test
    void deduplicatesBoatIdsAndCallsFleetOnce() {
        when(dynamicRatePolicy.requiresAdjustment(REGULAR_DATE)).thenReturn(false);
        when(fleetRatePort.findBaseRates(anySet())).thenReturn(Map.of(BOAT_A, BASE_RATE));
        when(dynamicRatePolicy.apply(BASE_RATE, REGULAR_DATE, null)).thenReturn(BASE_RATE);

        List<BaseRateResult> result = service.provideBaseRates(
                new ProvideBaseRateCommand(List.of(BOAT_A, BOAT_A), REGULAR_DATE));

        assertThat(result).containsExactly(new BaseRateResult(BOAT_A, BASE_RATE, null));
        verify(fleetRatePort).findBaseRates(anySet());
        verify(financialParametersRepository, never()).findCurrent();
    }

    @Test
    void doesNotReadParametersForARegularDate() {
        when(dynamicRatePolicy.requiresAdjustment(REGULAR_DATE)).thenReturn(false);
        when(fleetRatePort.findBaseRates(anySet())).thenReturn(Map.of(BOAT_A, BASE_RATE));
        when(dynamicRatePolicy.apply(BASE_RATE, REGULAR_DATE, null)).thenReturn(BASE_RATE);

        List<BaseRateResult> result = service.provideBaseRates(
                new ProvideBaseRateCommand(List.of(BOAT_A), REGULAR_DATE));

        assertThat(result).containsExactly(new BaseRateResult(BOAT_A, BASE_RATE, null));
        verify(financialParametersRepository, never()).findCurrent();
    }

    @Test
    void readsParametersOnceAndReturnsTheirIdWhenAdjustmentIsRequired() {
        FinancialParameters parameters = parameters();
        BaseRate adjusted = new BaseRate(new Money(new BigDecimal("385000.0000")));
        when(dynamicRatePolicy.requiresAdjustment(WEEKEND_DATE)).thenReturn(true);
        when(financialParametersRepository.findCurrent()).thenReturn(Optional.of(parameters));
        when(fleetRatePort.findBaseRates(anySet())).thenReturn(Map.of(BOAT_A, BASE_RATE));
        when(dynamicRatePolicy.apply(BASE_RATE, WEEKEND_DATE, parameters)).thenReturn(adjusted);

        List<BaseRateResult> result = service.provideBaseRates(
                new ProvideBaseRateCommand(List.of(BOAT_A), WEEKEND_DATE));

        assertThat(result).containsExactly(new BaseRateResult(BOAT_A, adjusted, parameters.id()));
        verify(financialParametersRepository).findCurrent();
    }

    @Test
    void keepsBoatsWithRatesWhenAnotherBoatHasNoRate() {
        when(dynamicRatePolicy.requiresAdjustment(REGULAR_DATE)).thenReturn(false);
        when(fleetRatePort.findBaseRates(anySet())).thenReturn(Map.of(BOAT_A, BASE_RATE));
        when(dynamicRatePolicy.apply(BASE_RATE, REGULAR_DATE, null)).thenReturn(BASE_RATE);

        List<BaseRateResult> result = service.provideBaseRates(
                new ProvideBaseRateCommand(List.of(BOAT_A, BOAT_B), REGULAR_DATE));

        assertThat(result).containsExactly(new BaseRateResult(BOAT_A, BASE_RATE, null));
        assertThat(failureRecorder.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.useCase()).isEqualTo("UC02");
            assertThat(failure.reservationId()).isNull();
            assertThat(failure.reason()).isEqualTo(BaseRateFailureReason.BASE_RATE_NOT_AVAILABLE.name());
            assertThat(failure.payloadRef()).contains("evaluatedDate=" + REGULAR_DATE)
                    .contains("boatId=" + BOAT_B.value());
        });
    }

    @Test
    void ignoresUnknownBoatsReturnedByFleet() {
        when(dynamicRatePolicy.requiresAdjustment(REGULAR_DATE)).thenReturn(false);
        when(fleetRatePort.findBaseRates(anySet())).thenReturn(Map.of(BOAT_A, BASE_RATE, BOAT_B, BASE_RATE));
        when(dynamicRatePolicy.apply(BASE_RATE, REGULAR_DATE, null)).thenReturn(BASE_RATE);

        List<BaseRateResult> result = service.provideBaseRates(
                new ProvideBaseRateCommand(List.of(BOAT_A), REGULAR_DATE));

        assertThat(result).containsExactly(new BaseRateResult(BOAT_A, BASE_RATE, null));
    }

    @Test
    void recordsAndPropagatesFleetFailuresWithoutReturningPartialResults() {
        BaseRateException fleetFailure = new BaseRateException(
                BaseRateFailureReason.FLEET_UNAVAILABLE, "Fleet is down");
        when(dynamicRatePolicy.requiresAdjustment(REGULAR_DATE)).thenReturn(false);
        when(fleetRatePort.findBaseRates(anySet())).thenThrow(fleetFailure);

        assertThatThrownBy(() -> service.provideBaseRates(
                new ProvideBaseRateCommand(List.of(BOAT_A), REGULAR_DATE)))
                .isSameAs(fleetFailure);

        assertThat(failureRecorder.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.useCase()).isEqualTo("UC02");
            assertThat(failure.reservationId()).isNull();
            assertThat(failure.reason()).isEqualTo(BaseRateFailureReason.FLEET_UNAVAILABLE.name());
            assertThat(failure.payloadRef()).contains("evaluatedDate=" + REGULAR_DATE)
                    .contains(BOAT_A.value().toString());
        });
    }

    @Test
    void recordsAndPropagatesMissingDynamicParametersWithoutCallingFleet() {
        when(dynamicRatePolicy.requiresAdjustment(WEEKEND_DATE)).thenReturn(true);
        when(financialParametersRepository.findCurrent()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.provideBaseRates(
                new ProvideBaseRateCommand(List.of(BOAT_A), WEEKEND_DATE)))
                .isInstanceOf(BaseRateException.class)
                .satisfies(exception -> assertThat(((BaseRateException) exception).reason())
                        .isEqualTo(BaseRateFailureReason.DYNAMIC_RATE_NOT_CONFIGURED));

        verifyNoInteractions(fleetRatePort);
        assertThat(failureRecorder.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.useCase()).isEqualTo("UC02");
            assertThat(failure.reservationId()).isNull();
            assertThat(failure.reason())
                    .isEqualTo(BaseRateFailureReason.DYNAMIC_RATE_NOT_CONFIGURED.name());
            assertThat(failure.payloadRef()).contains("evaluatedDate=" + WEEKEND_DATE)
                    .contains(BOAT_A.value().toString());
        });
    }

    private static FinancialParameters parameters() {
        return FinancialParameters.create(
                UUID.fromString("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d"),
                1,
                new Percentage(new BigDecimal("10.00")),
                new Money(new BigDecimal("15000.00")),
                new Percentage(new BigDecimal("10.00")),
                new Percentage(new BigDecimal("25.00")),
                "admin@seashare.com",
                Instant.parse("2026-09-20T15:04:05Z"));
    }
}
