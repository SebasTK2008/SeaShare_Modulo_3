package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.BaseRateResult;
import com.seashare.seasharem3.application.dto.BatchEstimateResult;
import com.seashare.seasharem3.application.dto.EstimateBatchCommand;
import com.seashare.seasharem3.application.dto.ProvideBaseRateCommand;
import com.seashare.seasharem3.application.dto.UnavailableBoat;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EstimateBatchServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 25);
    private static final Money INSURANCE = new Money(new BigDecimal("15000.00"));
    private static final Money BASE_RATE = new Money(new BigDecimal("350000.00"));

    @Mock
    private ProvideBaseRateUseCase baseRateUseCase;
    @Mock
    private FinancialParametersRepository paramsRepo;
    @Mock
    private ClockPort clock;

    private EstimateBatchService service;

    @BeforeEach
    void setUp() {
        service = new EstimateBatchService(baseRateUseCase, paramsRepo, clock, new PricingCalculator(), 50);
    }

    private static FinancialParameters parameters() {
        return FinancialParameters.create(UUID.randomUUID(), 1,
                new Percentage(new BigDecimal("10.00")),
                INSURANCE,
                new Percentage(new BigDecimal("10.00")),
                new Percentage(new BigDecimal("25.00")),
                "admin", Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static BoatId boat() {
        return new BoatId(UUID.randomUUID());
    }

    private static BaseRateResult rate(BoatId boatId) {
        return new BaseRateResult(boatId, new BaseRate(BASE_RATE), null);
    }

    @Test
    void emptyListReturnsEmptyWithoutReadingParametersOrCallingUc02() {
        when(clock.today()).thenReturn(TODAY);

        BatchEstimateResult result = service.estimate(new EstimateBatchCommand(List.of()));

        assertThat(result.evaluationDate()).isEqualTo(TODAY);
        assertThat(result.durationDays()).isEqualTo(1);
        assertThat(result.passengers()).isEqualTo(1);
        assertThat(result.estimates()).isEmpty();
        assertThat(result.unavailable()).isEmpty();
        verifyNoInteractions(paramsRepo, baseRateUseCase);
    }

    @Test
    void singleBoatEstimateReturnsExactTotal() {
        BoatId id = boat();
        when(clock.today()).thenReturn(TODAY);
        when(paramsRepo.findCurrent()).thenReturn(Optional.of(parameters()));
        when(baseRateUseCase.provideBaseRates(any())).thenReturn(List.of(rate(id)));

        BatchEstimateResult result = service.estimate(new EstimateBatchCommand(List.of(id)));

        assertThat(result.estimates()).hasSize(1);
        assertThat(result.estimates().get(0).boatId()).isEqualTo(id);
        assertThat(result.estimates().get(0).estimatedTotal().toOutputAmount())
                .isEqualByComparingTo(new BigDecimal("365000.00"));
        assertThat(result.unavailable()).isEmpty();
    }

    @Test
    void boatMissingFromUc02ResultIsReportedInUnavailable() {
        BoatId priced = boat();
        BoatId unpriced = boat();
        when(clock.today()).thenReturn(TODAY);
        when(paramsRepo.findCurrent()).thenReturn(Optional.of(parameters()));
        when(baseRateUseCase.provideBaseRates(any())).thenReturn(List.of(rate(priced)));

        BatchEstimateResult result = service.estimate(new EstimateBatchCommand(List.of(priced, unpriced)));

        assertThat(result.estimates()).extracting(e -> e.boatId()).containsExactly(priced);
        assertThat(result.unavailable()).containsExactly(
                new UnavailableBoat(unpriced, UnavailableBoat.NO_BASE_RATE));
    }

    @Test
    void estimatesAndUnavailableCoverEveryUniqueRequestedBoat() {
        List<BoatId> boats = IntStream.range(0, 5).mapToObj(i -> boat()).toList();
        when(clock.today()).thenReturn(TODAY);
        when(paramsRepo.findCurrent()).thenReturn(Optional.of(parameters()));
        when(baseRateUseCase.provideBaseRates(any()))
                .thenReturn(List.of(rate(boats.get(0)), rate(boats.get(3))));

        BatchEstimateResult result = service.estimate(new EstimateBatchCommand(boats));

        assertThat(result.estimates()).hasSize(2);
        assertThat(result.unavailable()).hasSize(3);
        assertThat(result.unavailable()).extracting(UnavailableBoat::boatId)
                .containsExactly(boats.get(1), boats.get(2), boats.get(4));
        assertThat(result.unavailable()).allMatch(
                item -> UnavailableBoat.NO_BASE_RATE.equals(item.reason()));
    }

    @Test
    void duplicateBoatIdsProduceOneQueryOneEstimateAndNoUnavailable() {
        BoatId id = boat();
        FinancialParameters current = parameters();
        when(clock.today()).thenReturn(TODAY);
        when(paramsRepo.findCurrent()).thenReturn(Optional.of(current));
        when(baseRateUseCase.provideBaseRates(any())).thenReturn(List.of(rate(id)));

        BatchEstimateResult result = service.estimate(
                new EstimateBatchCommand(List.of(id, id, id)));

        ArgumentCaptor<ProvideBaseRateCommand> captor = ArgumentCaptor.forClass(ProvideBaseRateCommand.class);
        verify(baseRateUseCase).provideBaseRates(captor.capture());
        assertThat(captor.getValue().boatIds()).containsExactly(id);
        assertThat(captor.getValue().evaluatedDate()).isEqualTo(TODAY);
        assertThat(captor.getValue().parameters()).isSameAs(current);
        assertThat(result.estimates()).hasSize(1);
        assertThat(result.unavailable()).isEmpty();
    }

    @Test
    void ignoresBaseRatesForBoatsThatWereNotRequested() {
        BoatId requested = boat();
        when(clock.today()).thenReturn(TODAY);
        when(paramsRepo.findCurrent()).thenReturn(Optional.of(parameters()));
        when(baseRateUseCase.provideBaseRates(any()))
                .thenReturn(List.of(rate(requested), rate(boat())));

        BatchEstimateResult result = service.estimate(new EstimateBatchCommand(List.of(requested)));

        assertThat(result.estimates()).hasSize(1);
        assertThat(result.estimates().get(0).boatId()).isEqualTo(requested);
        assertThat(result.unavailable()).isEmpty();
    }

    @Test
    void failsWhenFinancialParametersAreNotConfigured() {
        BoatId id = boat();
        when(clock.today()).thenReturn(TODAY);
        when(paramsRepo.findCurrent()).thenReturn(Optional.empty());

        ContractException exception = catchThrowableOfType(
                () -> service.estimate(new EstimateBatchCommand(List.of(id))),
                ContractException.class);

        assertThat(exception.code()).isEqualTo(ErrorCode.FINANCIAL_PARAMETERS_NOT_CONFIGURED);
        verifyNoInteractions(baseRateUseCase);
    }

    @Test
    void propagatesFleetUnavailableWithoutPartialEstimates() {
        BoatId id = boat();
        when(clock.today()).thenReturn(TODAY);
        when(paramsRepo.findCurrent()).thenReturn(Optional.of(parameters()));
        when(baseRateUseCase.provideBaseRates(any()))
                .thenThrow(new BaseRateException(BaseRateFailureReason.FLEET_UNAVAILABLE, "fleet down"));

        assertThatThrownBy(() -> service.estimate(new EstimateBatchCommand(List.of(id))))
                .isInstanceOf(BaseRateException.class)
                .extracting(e -> ((BaseRateException) e).reason())
                .isEqualTo(BaseRateFailureReason.FLEET_UNAVAILABLE);
    }

    @Test
    void rejectsBatchLargerThanDefaultLimitWithoutCallingDependencies() {
        List<BoatId> boats = IntStream.range(0, 51).mapToObj(i -> boat()).toList();

        ContractException exception = catchThrowableOfType(
                () -> service.estimate(new EstimateBatchCommand(boats)),
                ContractException.class);

        assertThat(exception.code()).isEqualTo(ErrorCode.BATCH_SIZE_EXCEEDED);
        verifyNoInteractions(paramsRepo, baseRateUseCase, clock);
    }

    @Test
    void limitComesFromConfigurationAndIsInclusive() {
        EstimateBatchService limited = new EstimateBatchService(
                baseRateUseCase, paramsRepo, clock, new PricingCalculator(), 2);
        List<BoatId> boats = IntStream.range(0, 3).mapToObj(i -> boat()).toList();

        ContractException exception = catchThrowableOfType(
                () -> limited.estimate(new EstimateBatchCommand(boats)),
                ContractException.class);
        assertThat(exception.code()).isEqualTo(ErrorCode.BATCH_SIZE_EXCEEDED);

        when(clock.today()).thenReturn(TODAY);
        when(paramsRepo.findCurrent()).thenReturn(Optional.of(parameters()));
        when(baseRateUseCase.provideBaseRates(any())).thenReturn(List.of());

        BatchEstimateResult result = limited.estimate(
                new EstimateBatchCommand(List.of(boats.get(0), boats.get(1))));
        assertThat(result.estimates()).isEmpty();
        assertThat(result.unavailable()).hasSize(2);
    }

    @Test
    void rejectsNullElementInBoatIds() {
        List<BoatId> boats = new ArrayList<>();
        boats.add(boat());
        boats.add(null);

        ContractException exception = catchThrowableOfType(
                () -> service.estimate(new EstimateBatchCommand(boats)),
                ContractException.class);

        assertThat(exception.code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        verifyNoInteractions(paramsRepo, baseRateUseCase);
        verify(clock, never()).today();
    }

    @Test
    void rejectsNullCommand() {
        assertThatThrownBy(() -> service.estimate(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullBoatIds() {
        assertThatThrownBy(() -> service.estimate(new EstimateBatchCommand(null)))
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(paramsRepo, baseRateUseCase);
    }

    @Test
    void rejectsNonPositiveConfiguredLimit() {
        assertThatThrownBy(() -> new EstimateBatchService(
                baseRateUseCase, paramsRepo, clock, new PricingCalculator(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxBatchSize");
    }
}
