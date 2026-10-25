package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.*;
import com.seashare.seasharem3.application.exception.ContractException;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.EstimateBatchUseCase;
import com.seashare.seasharem3.application.port.in.ProvideBaseRateUseCase;
import com.seashare.seasharem3.application.port.out.ClockPort;
import com.seashare.seasharem3.application.port.out.FinancialParametersRepository;
import com.seashare.seasharem3.domain.exception.BaseRateException;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.service.PricingCalculator;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

/**
 * Servicio de aplicacion UC01 - estimacion en lote (HU1).
 * Orquesta UC02 (ProvideBaseRateUseCase), lee parametros vigentes (UC11 / FinancialParametersRepository),
 * aplica PricingCalculator (dominio puro). No persiste; solo lectura. Usa USE_CASE="UC01".
 * Fuente: UC01 plan T008/T009/T010; contrato rest/UC01-estimacion-lote.md §3; SPEC RF-001..RF-007.
 */
@Service
public class EstimateBatchService implements EstimateBatchUseCase {

    private final ProvideBaseRateUseCase baseRateUseCase;
    private final FinancialParametersRepository paramsRepo;
    private final ClockPort clock;
    private final PricingCalculator pricingCalculator = new PricingCalculator();

    public EstimateBatchService(ProvideBaseRateUseCase baseRateUseCase,
                               FinancialParametersRepository paramsRepo,
                               ClockPort clock) {
        this.baseRateUseCase = Objects.requireNonNull(baseRateUseCase, "baseRateUseCase");
        this.paramsRepo = Objects.requireNonNull(paramsRepo, "paramsRepo");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public BatchEstimateResult estimate(EstimateBatchCommand command) {
        FinancialParameters current = paramsRepo.findCurrent()
                .orElseThrow(() -> new ContractException(ErrorCode.FINANCIAL_PARAMETERS_NOT_CONFIGURED,
                        "Financial parameters not configured."));

        Money insuranceFee = current.insuranceFeePerPassenger();
        LocalDate evaluationDate = clock.today();

        List<BaseRateResult> baseRates;
        Set<BoatId> uniqueBoatIds = new LinkedHashSet<>(Objects.requireNonNull(command.boatIds(), "boatIds"));
        try {
            baseRates = baseRateUseCase.provideBaseRates(
                    new ProvideBaseRateCommand(new ArrayList<>(uniqueBoatIds), evaluationDate));
        } catch (BaseRateException ex) {
            if (ex.reason().equals(BaseRateException.BaseRateFailureReason.FLEET_UNAVAILABLE)) {
                throw new ContractException(ErrorCode.FLEET_UNAVAILABLE, "Fleet service unavailable.");
            }
            throw ex;
        }

        List<BoatEstimate> estimates = new ArrayList<>();
        for (BaseRateResult result : baseRates) {
            BigDecimal total = pricingCalculator.calculate(
                    result.baseRate(), 1, 1, insuranceFee);
            estimates.add(new BoatEstimate(result.boatId(), new Money(total)));
        }

        return new BatchEstimateResult(
                evaluationDate, 1, 1, List.copyOf(estimates), List.copyOf(Collections.emptyList()));
    }
}
