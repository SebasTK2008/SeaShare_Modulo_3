package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.*;
import com.seashare.seasharem3.application.exception.ContractException;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.EstimateSingleUseCase;
import com.seashare.seasharem3.application.port.in.ProvideBaseRateUseCase;
import com.seashare.seasharem3.application.port.out.FinancialParametersRepository;
import com.seashare.seasharem3.domain.exception.BaseRateException;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.service.PricingCalculator;
import com.seashare.seasharem3.domain.valueobject.Money;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.Collections;

public class EstimateSingleService implements EstimateSingleUseCase {

    private final ProvideBaseRateUseCase baseRateUseCase;
    private final FinancialParametersRepository paramsRepo;
    private final PricingCalculator pricingCalculator;

    public EstimateSingleService(ProvideBaseRateUseCase baseRateUseCase,
                                 FinancialParametersRepository paramsRepo,
                                 PricingCalculator pricingCalculator) {
        this.baseRateUseCase = baseRateUseCase;
        this.paramsRepo = paramsRepo;
        this.pricingCalculator = pricingCalculator;
    }

    @Override
    public SingleEstimateResult estimate(EstimateSingleCommand command) {
        if (command.startDate().isAfter(command.endDate())) {
            throw new ContractException(ErrorCode.VALIDATION_ERROR,
                    "start_date must not be after end_date.");
        }
        if (command.passengers() < 1) {
            throw new ContractException(ErrorCode.VALIDATION_ERROR,
                    "passengers must be at least 1.");
        }

        FinancialParameters current = paramsRepo.findCurrent()
                .orElseThrow(() -> new ContractException(ErrorCode.FINANCIAL_PARAMETERS_NOT_CONFIGURED,
                        "Financial parameters not configured."));
        Money insuranceFee = current.insuranceFeePerPassenger();

        int durationDays = (int) ChronoUnit.DAYS.between(command.startDate(), command.endDate()) + 1;

        try {
            var baseRates = baseRateUseCase.provideBaseRates(
                    new ProvideBaseRateCommand(
                            Collections.singletonList(command.boatId()),
                            command.startDate()));

            if (baseRates.isEmpty()) {
                throw new ContractException(ErrorCode.BASE_RATE_NOT_AVAILABLE,
                        "Base rate not available for boat.");
            }

            var result = baseRates.get(0);
            BigDecimal total = pricingCalculator.calculate(
                    result.baseRate(), durationDays, command.passengers(), insuranceFee);

            return new SingleEstimateResult(
                    result.boatId(),
                    command.startDate(),
                    command.endDate(),
                    durationDays,
                    command.passengers(),
                    new Money(total),
                    EstimateWarning.TEXT);
        } catch (BaseRateException ex) {
            if (ex.reason().equals(BaseRateException.BaseRateFailureReason.FLEET_UNAVAILABLE)) {
                throw new ContractException(ErrorCode.FLEET_UNAVAILABLE, "Fleet service unavailable.");
            }
            throw ex;
        }
    }
}
