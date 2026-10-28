package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.BaseRateResult;
import com.seashare.seasharem3.application.dto.EstimateSingleCommand;
import com.seashare.seasharem3.application.dto.EstimateWarning;
import com.seashare.seasharem3.application.dto.ProvideBaseRateCommand;
import com.seashare.seasharem3.application.dto.SingleEstimateResult;
import com.seashare.seasharem3.application.exception.ContractException;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.ProvideBaseRateUseCase;
import com.seashare.seasharem3.application.port.out.ClockPort;
import com.seashare.seasharem3.application.port.out.FinancialParametersRepository;
import com.seashare.seasharem3.domain.exception.BaseRateException;
import com.seashare.seasharem3.domain.exception.BaseRateException.BaseRateFailureReason;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.service.PricingCalculator;
import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EstimateSingleServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 25);
    private static final Money INSURANCE = new Money(new BigDecimal("15000.00"));
    private static final Money BASE_RATE = new Money(new BigDecimal("350000.00"));

    @Mock
    private ProvideBaseRateUseCase baseRateUseCase;
    @Mock
    private FinancialParametersRepository paramsRepo;
    @Mock
    private ClockPort clock;

    private EstimateSingleService service;
    private BoatId boatId;

    @BeforeEach
    void setUp() {
        service = new EstimateSingleService(baseRateUseCase, paramsRepo, clock, new PricingCalculator());
        boatId = new BoatId(UUID.randomUUID());
    }

    private static FinancialParameters parameters() {
        return FinancialParameters.create(UUID.randomUUID(), 1,
                new Percentage(new BigDecimal("10.00")),
                INSURANCE,
                new Percentage(new BigDecimal("10.00")),
                new Percentage(new BigDecimal("25.00")),
                "admin", Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static LocalDate day(int daysFromToday) {
        return TODAY.plusDays(daysFromToday);
    }

    @Test
    void calculatesTotalForInclusiveDateRangeAndPassengers() {
        when(clock.today()).thenReturn(TODAY);
        when(paramsRepo.findCurrent()).thenReturn(Optional.of(parameters()));
        when(baseRateUseCase.provideBaseRates(any())).thenReturn(List.of(
                new BaseRateResult(boatId, new BaseRate(BASE_RATE), null)));

        SingleEstimateResult result = service.estimate(
                new EstimateSingleCommand(boatId, day(1), day(3), 4));

        assertThat(result.durationDays()).isEqualTo(3);
        // 350000 * 3 + 15000 * 4
        assertThat(result.estimatedTotal().toOutputAmount())
                .isEqualByComparingTo(new BigDecimal("1110000.00"));
        assertThat(result.warning()).isEqualTo(EstimateWarning.TEXT);
        assertThat(result.boatId()).isEqualTo(boatId);
        assertThat(result.startDate()).isEqualTo(day(1));
        assertThat(result.endDate()).isEqualTo(day(3));
    }

    @Test
    void sameStartAndEndDateCountsAsOneDay() {
        when(clock.today()).thenReturn(TODAY);
        when(paramsRepo.findCurrent()).thenReturn(Optional.of(parameters()));
        when(baseRateUseCase.provideBaseRates(any())).thenReturn(List.of(
                new BaseRateResult(boatId, new BaseRate(BASE_RATE), null)));

        SingleEstimateResult result = service.estimate(
                new EstimateSingleCommand(boatId, day(2), day(2), 1));

        assertThat(result.durationDays()).isEqualTo(1);
        assertThat(result.estimatedTotal().toOutputAmount())
                .isEqualByComparingTo(new BigDecimal("365000.00"));
    }

    @Test
    void startDateTodayIsAccepted() {
        when(clock.today()).thenReturn(TODAY);
        when(paramsRepo.findCurrent()).thenReturn(Optional.of(parameters()));
        when(baseRateUseCase.provideBaseRates(any())).thenReturn(List.of(
                new BaseRateResult(boatId, new BaseRate(BASE_RATE), null)));

        SingleEstimateResult result = service.estimate(
                new EstimateSingleCommand(boatId, TODAY, TODAY, 1));

        assertThat(result.durationDays()).isEqualTo(1);
    }

    @Test
    void queriesUc02WithTheStartDate() {
        FinancialParameters current = parameters();
        when(clock.today()).thenReturn(TODAY);
        when(paramsRepo.findCurrent()).thenReturn(Optional.of(current));
        when(baseRateUseCase.provideBaseRates(any())).thenReturn(List.of(
                new BaseRateResult(boatId, new BaseRate(BASE_RATE), null)));

        service.estimate(new EstimateSingleCommand(boatId, day(5), day(7), 2));

        ArgumentCaptor<ProvideBaseRateCommand> captor = ArgumentCaptor.forClass(ProvideBaseRateCommand.class);
        verify(baseRateUseCase).provideBaseRates(captor.capture());
        assertThat(captor.getValue().boatIds()).containsExactly(boatId);
        assertThat(captor.getValue().evaluatedDate()).isEqualTo(day(5));
        assertThat(captor.getValue().parameters()).isSameAs(current);
    }

    @Test
    void rejectsStartDateInThePast() {
        when(clock.today()).thenReturn(TODAY);

        ContractException exception = catchThrowableOfType(
                () -> service.estimate(new EstimateSingleCommand(boatId, day(-1), day(3), 1)),
                ContractException.class);

        assertThat(exception.code()).isEqualTo(ErrorCode.INVALID_DATE_RANGE);
        verifyNoInteractions(paramsRepo, baseRateUseCase);
    }

    @Test
    void rejectsEndDateBeforeStartDate() {
        when(clock.today()).thenReturn(TODAY);

        ContractException exception = catchThrowableOfType(
                () -> service.estimate(new EstimateSingleCommand(boatId, day(3), day(2), 1)),
                ContractException.class);

        assertThat(exception.code()).isEqualTo(ErrorCode.INVALID_DATE_RANGE);
        verifyNoInteractions(paramsRepo, baseRateUseCase);
    }

    @Test
    void rejectsNonPositivePassengers() {
        ContractException exception = catchThrowableOfType(
                () -> service.estimate(new EstimateSingleCommand(boatId, day(1), day(2), 0)),
                ContractException.class);

        assertThat(exception.code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        verifyNoInteractions(paramsRepo, baseRateUseCase);
    }

    @Test
    void failsWhenFinancialParametersAreNotConfigured() {
        when(clock.today()).thenReturn(TODAY);
        when(paramsRepo.findCurrent()).thenReturn(Optional.empty());

        ContractException exception = catchThrowableOfType(
                () -> service.estimate(new EstimateSingleCommand(boatId, day(1), day(2), 1)),
                ContractException.class);

        assertThat(exception.code()).isEqualTo(ErrorCode.FINANCIAL_PARAMETERS_NOT_CONFIGURED);
        verifyNoInteractions(baseRateUseCase);
    }

    @Test
    void failsWhenNoBaseRateIsAvailable() {
        when(clock.today()).thenReturn(TODAY);
        when(paramsRepo.findCurrent()).thenReturn(Optional.of(parameters()));
        when(baseRateUseCase.provideBaseRates(any())).thenReturn(List.of());

        ContractException exception = catchThrowableOfType(
                () -> service.estimate(new EstimateSingleCommand(boatId, day(1), day(2), 1)),
                ContractException.class);

        assertThat(exception.code()).isEqualTo(ErrorCode.BASE_RATE_NOT_AVAILABLE);
    }

    @Test
    void propagatesFleetUnavailable() {
        when(clock.today()).thenReturn(TODAY);
        when(paramsRepo.findCurrent()).thenReturn(Optional.of(parameters()));
        when(baseRateUseCase.provideBaseRates(any()))
                .thenThrow(new BaseRateException(BaseRateFailureReason.FLEET_UNAVAILABLE, "fleet down"));

        assertThatThrownBy(() -> service.estimate(
                new EstimateSingleCommand(boatId, day(1), day(2), 1)))
                .isInstanceOf(BaseRateException.class)
                .extracting(e -> ((BaseRateException) e).reason())
                .isEqualTo(BaseRateFailureReason.FLEET_UNAVAILABLE);
    }

    @Test
    void rejectsIncompleteCommand() {
        assertThatThrownBy(() -> service.estimate(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.estimate(
                new EstimateSingleCommand(null, day(1), day(2), 1)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.estimate(
                new EstimateSingleCommand(boatId, null, day(2), 1)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.estimate(
                new EstimateSingleCommand(boatId, day(1), null, 1)))
                .isInstanceOf(NullPointerException.class);
    }
}
