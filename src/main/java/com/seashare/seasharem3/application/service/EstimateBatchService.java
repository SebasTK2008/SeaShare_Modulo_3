package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.BaseRateResult;
import com.seashare.seasharem3.application.dto.BatchEstimateResult;
import com.seashare.seasharem3.application.dto.BoatEstimate;
import com.seashare.seasharem3.application.dto.EstimateBatchCommand;
import com.seashare.seasharem3.application.dto.ProvideBaseRateCommand;
import com.seashare.seasharem3.application.dto.UnavailableBoat;
import com.seashare.seasharem3.application.exception.ContractException;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.EstimateBatchUseCase;
import com.seashare.seasharem3.application.port.in.ProvideBaseRateUseCase;
import com.seashare.seasharem3.application.port.out.ClockPort;
import com.seashare.seasharem3.application.port.out.FinancialParametersRepository;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.service.PricingCalculator;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Servicio de aplicacion UC01 - estimacion en lote (HU1).
 * Orquesta UC02 (ProvideBaseRateUseCase), lee parametros vigentes (UC11 / FinancialParametersRepository),
 * aplica PricingCalculator (dominio puro). No persiste; solo lectura. Usa USE_CASE="UC01".
 *
 * <p>Lista vacia: responde 200 con arreglos vacios sin leer parametros ni invocar UC02 (contrato
 * lote regla 1). Toda embarcacion solicitada que UC02 no devuelve se reporta en {@code unavailable[]}
 * con {@link UnavailableBoat#NO_BASE_RATE} (contrato lote §4, decision A4).</p>
 *
 * Fuente: UC01 plan T008/T009/T010; contrato rest/UC01-estimacion-lote.md §3; SPEC RF-001..RF-007.
 */
public class EstimateBatchService implements EstimateBatchUseCase {

    private static final int BATCH_DURATION_DAYS = 1;
    private static final int BATCH_PASSENGERS = 1;

    private final ProvideBaseRateUseCase baseRateUseCase;
    private final FinancialParametersRepository paramsRepo;
    private final ClockPort clock;
    private final PricingCalculator pricingCalculator;
    private final int maxBatchSize;

    public EstimateBatchService(ProvideBaseRateUseCase baseRateUseCase,
                                FinancialParametersRepository paramsRepo,
                                ClockPort clock,
                                PricingCalculator pricingCalculator,
                                int maxBatchSize) {
        this.baseRateUseCase = Objects.requireNonNull(baseRateUseCase, "baseRateUseCase");
        this.paramsRepo = Objects.requireNonNull(paramsRepo, "paramsRepo");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.pricingCalculator = Objects.requireNonNull(pricingCalculator, "pricingCalculator");
        if (maxBatchSize < 1) {
            throw new IllegalArgumentException("maxBatchSize must be at least 1: " + maxBatchSize);
        }
        this.maxBatchSize = maxBatchSize;
    }

    @Override
    public BatchEstimateResult estimate(EstimateBatchCommand command) {
        Objects.requireNonNull(command, "command");
        List<BoatId> requested = Objects.requireNonNull(command.boatIds(), "boatIds");

        if (requested.size() > maxBatchSize) {
            throw new ContractException(ErrorCode.BATCH_SIZE_EXCEEDED,
                    "The request contains " + requested.size()
                            + " identifiers and the maximum allowed is " + maxBatchSize + ".");
        }

        Set<BoatId> uniqueBoatIds = new LinkedHashSet<>();
        for (BoatId boatId : requested) {
            if (boatId == null) {
                throw new ContractException(ErrorCode.VALIDATION_ERROR,
                        "boat_ids must not contain null elements.");
            }
            uniqueBoatIds.add(boatId);
        }

        LocalDate evaluationDate = clock.today();
        if (uniqueBoatIds.isEmpty()) {
            return new BatchEstimateResult(evaluationDate, BATCH_DURATION_DAYS, BATCH_PASSENGERS,
                    List.of(), List.of());
        }

        FinancialParameters current = paramsRepo.findCurrent()
                .orElseThrow(() -> new ContractException(ErrorCode.FINANCIAL_PARAMETERS_NOT_CONFIGURED,
                        "Financial parameters not configured."));
        Money insuranceFee = current.insuranceFeePerPassenger();

        List<BaseRateResult> baseRates = baseRateUseCase.provideBaseRates(
                new ProvideBaseRateCommand(new ArrayList<>(uniqueBoatIds), evaluationDate));

        List<BoatEstimate> estimates = new ArrayList<>();
        Set<BoatId> estimatedBoatIds = new LinkedHashSet<>();
        for (BaseRateResult result : baseRates) {
            if (result == null || !uniqueBoatIds.contains(result.boatId())) {
                continue;
            }
            Money total = pricingCalculator.estimate(
                    result.baseRate().amount(), BATCH_DURATION_DAYS, insuranceFee, BATCH_PASSENGERS);
            estimates.add(new BoatEstimate(result.boatId(), total));
            estimatedBoatIds.add(result.boatId());
        }

        List<UnavailableBoat> unavailable = new ArrayList<>();
        for (BoatId boatId : uniqueBoatIds) {
            if (!estimatedBoatIds.contains(boatId)) {
                unavailable.add(new UnavailableBoat(boatId, UnavailableBoat.NO_BASE_RATE));
            }
        }

        return new BatchEstimateResult(evaluationDate, BATCH_DURATION_DAYS, BATCH_PASSENGERS,
                List.copyOf(estimates), List.copyOf(unavailable));
    }
}
