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
import com.seashare.seasharem3.domain.valueobject.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Servicio de aplicacion UC01 - estimacion en lote (HU1).
 * Orquesta UC02, lee parametros vigentes (UC11), aplica PricingCalculator.
 */
public class EstimateBatchService implements EstimateBatchUseCase {

    private final ProvideBaseRateUseCase baseRateUseCase;
    private final FinancialParametersRepository paramsRepo;
    private final ClockPort clock;
    private final PricingCalculator pricingCalculator;

    public EstimateBatchService(ProvideBaseRateUseCase baseRateUseCase,
                               FinancialParametersRepository paramsRepo,
                               ClockPort clock,
                               PricingCalculator pricingCalculator) {
        this.baseRateUseCase = baseRateUseCase;
        this.paramsRepo = paramsRepo;
        this.clock = clock;
        this.pricingCalculator = pricingCalculator;
    }

    @Override
    public BatchEstimateResult estimate(EstimateBatchCommand command) {
        FinancialParameters current = paramsRepo.findCurrent()
                .orElseThrow(() -> new ContractException(ErrorCode.FINANCIAL_PARAMETERS_NOT_CONFIGURED,
                        "Financial parameters not configured."));

        Money insuranceFee = current.insuranceFeePerPassenger();
        LocalDate evaluationDate = clock.today();

        List<BaseRateResult> baseRates;
        try {
            baseRates = baseRateUseCase.provideBaseRates(
                    new ProvideBaseRateCommand(command.boatIds(), evaluationDate));
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
                evaluationDate, 1, 1, estimates, Collections.emptyList());
    }
}
