package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.*;
import com.seashare.seasharem3.application.port.in.ProvideBaseRateUseCase;
import com.seashare.seasharem3.application.port.out.ClockPort;
import com.seashare.seasharem3.application.port.out.FinancialParametersRepository;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.service.PricingCalculator;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EstimateBatchServiceTest {

    @Mock
    private ProvideBaseRateUseCase baseRateUseCase;
    @Mock
    private FinancialParametersRepository paramsRepo;
    @Mock
    private ClockPort clock;

    private EstimateBatchService service;

    @BeforeEach
    void setUp() {
        service = new EstimateBatchService(baseRateUseCase, paramsRepo, clock);
    }

    @Test
    void emptyListReturnsEmpty() {
        when(paramsRepo.findCurrent()).thenReturn(Optional.of(
                FinancialParameters.create(UUID.randomUUID(), 1,
                        new com.seashare.seasharem3.domain.valueobject.Percentage(new BigDecimal("10.00")),
                        new Money(new BigDecimal("15000.00")),
                        new com.seashare.seasharem3.domain.valueobject.Percentage(new BigDecimal("10.00")),
                        new com.seashare.seasharem3.domain.valueobject.Percentage(new BigDecimal("25.00")),
                        null, Instant.now())));
        when(clock.today()).thenReturn(LocalDate.of(2026, 9, 30));
        when(baseRateUseCase.provideBaseRates(any())).thenReturn(List.of());

        BatchEstimateResult result = service.estimate(new EstimateBatchCommand(Collections.emptyList()));
        assertThat(result.estimates()).isEmpty();
        assertThat(result.unavailable()).isEmpty();
        assertThat(result.durationDays()).isEqualTo(1);
    }

    @Test
    void singleBoatEstimate() {
        when(paramsRepo.findCurrent()).thenReturn(Optional.of(
                FinancialParameters.create(UUID.randomUUID(), 1,
                        new com.seashare.seasharem3.domain.valueobject.Percentage(new BigDecimal("10.00")),
                        new Money(new BigDecimal("15000.00")),
                        new com.seashare.seasharem3.domain.valueobject.Percentage(new BigDecimal("10.00")),
                        new com.seashare.seasharem3.domain.valueobject.Percentage(new BigDecimal("25.00")),
                        null, Instant.now())));
        when(clock.today()).thenReturn(LocalDate.of(2026, 9, 30));
        BoatId id = new BoatId(UUID.randomUUID());
        when(baseRateUseCase.provideBaseRates(any())).thenReturn(List.of(
                new BaseRateResult(id, new com.seashare.seasharem3.domain.valueobject.BaseRate(
                        new Money(new BigDecimal("350000.00"))), UUID.randomUUID())));

        BatchEstimateResult result = service.estimate(new EstimateBatchCommand(List.of(id)));
        assertThat(result.estimates()).hasSize(1);
        assertThat(result.estimates().get(0).boatId()).isEqualTo(id);
    }
}
