package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.EstimateSingleCommand;
import com.seashare.seasharem3.application.dto.EstimateWarning;
import com.seashare.seasharem3.application.dto.ProvideBaseRateCommand;
import com.seashare.seasharem3.application.dto.SingleEstimateResult;
import com.seashare.seasharem3.application.exception.ContractException;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.EstimateSingleUseCase;
import com.seashare.seasharem3.application.port.in.ProvideBaseRateUseCase;
import com.seashare.seasharem3.application.port.out.ClockPort;
import com.seashare.seasharem3.application.port.out.FinancialParametersRepository;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.service.PricingCalculator;
import com.seashare.seasharem3.domain.valueobject.Money;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Servicio de aplicacion UC01 - estimacion individual (HU2).
 * Usa fechas inclusivas, calcula duracion, incluye advertencia obligatoria.
 *
 * <p>Validacion de fechas en la capa de aplicacion con {@link ClockPort} (zona America/Bogota):
 * inicio en el pasado o fin anterior al inicio devuelven {@code 400 INVALID_DATE_RANGE} (contrato
 * individual §3 reglas 1-2).</p>
 *
 * Usa USE_CASE="UC01". Fuente: UC01 plan T014/T015; contrato rest/UC01-estimacion-individual.md §3.
 */
public class EstimateSingleService implements EstimateSingleUseCase {

    private final ProvideBaseRateUseCase baseRateUseCase;
    private final FinancialParametersRepository paramsRepo;
    private final ClockPort clock;
    private final PricingCalculator pricingCalculator;

    public EstimateSingleService(ProvideBaseRateUseCase baseRateUseCase,
                                 FinancialParametersRepository paramsRepo,
                                 ClockPort clock,
                                 PricingCalculator pricingCalculator) {
        this.baseRateUseCase = Objects.requireNonNull(baseRateUseCase, "baseRateUseCase");
        this.paramsRepo = Objects.requireNonNull(paramsRepo, "paramsRepo");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.pricingCalculator = Objects.requireNonNull(pricingCalculator, "pricingCalculator");
    }

    @Override
    public SingleEstimateResult estimate(EstimateSingleCommand command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.boatId(), "boatId");
        Objects.requireNonNull(command.startDate(), "startDate");
        Objects.requireNonNull(command.endDate(), "endDate");

        if (command.passengers() < 1) {
            throw new ContractException(ErrorCode.VALIDATION_ERROR,
                    "passengers must be at least 1.");
        }

        LocalDate today = clock.today();
        if (command.startDate().isBefore(today)) {
            throw new ContractException(ErrorCode.INVALID_DATE_RANGE,
                    "start_date must not be in the past.");
        }
        if (command.endDate().isBefore(command.startDate())) {
            throw new ContractException(ErrorCode.INVALID_DATE_RANGE,
                    "end_date must not be before start_date.");
        }

        FinancialParameters current = paramsRepo.findCurrent()
                .orElseThrow(() -> new ContractException(ErrorCode.FINANCIAL_PARAMETERS_NOT_CONFIGURED,
                        "Financial parameters not configured."));
        Money insuranceFee = current.insuranceFeePerPassenger();

        int durationDays = (int) ChronoUnit.DAYS.between(command.startDate(), command.endDate()) + 1;

        List<com.seashare.seasharem3.application.dto.BaseRateResult> baseRates =
                baseRateUseCase.provideBaseRates(new ProvideBaseRateCommand(
                        Collections.singletonList(command.boatId()),
                        command.startDate(),
                        current));

        if (baseRates.isEmpty()) {
            throw new ContractException(ErrorCode.BASE_RATE_NOT_AVAILABLE,
                    "Base rate not available for boat.");
        }

        Money total = pricingCalculator.estimate(
                baseRates.get(0).baseRate().amount(), durationDays, insuranceFee, command.passengers());

        return new SingleEstimateResult(
                command.boatId(),
                command.startDate(),
                command.endDate(),
                durationDays,
                command.passengers(),
                total,
                EstimateWarning.TEXT);
    }
}
