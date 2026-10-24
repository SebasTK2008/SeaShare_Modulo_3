package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.BaseRateResult;
import com.seashare.seasharem3.application.dto.ProvideBaseRateCommand;
import com.seashare.seasharem3.application.port.in.ProvideBaseRateUseCase;
import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.application.port.out.FinancialParametersRepository;
import com.seashare.seasharem3.application.port.out.FleetRatePort;
import com.seashare.seasharem3.domain.exception.BaseRateException;
import com.seashare.seasharem3.domain.exception.BaseRateException.BaseRateFailureReason;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.service.DynamicRatePolicy;
import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.BoatId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orquesta la consulta batch a Flota y aplica la tarifa dinámica de UC02.
 *
 * <p>No tiene anotaciones de Spring: el bean lo construye {@code FleetRateConfiguration}. La fecha
 * evaluada llega en el comando; UC02 no depende de {@code ClockPort}. Fuente: UC02 plan T016.</p>
 */
public class ProvideBaseRateService implements ProvideBaseRateUseCase {

    private static final String USE_CASE = "UC02";

    private final FleetRatePort fleetRatePort;
    private final FinancialParametersRepository financialParametersRepository;
    private final DynamicRatePolicy dynamicRatePolicy;
    private final FailureRecorderPort failureRecorder;

    public ProvideBaseRateService(FleetRatePort fleetRatePort,
            FinancialParametersRepository financialParametersRepository,
            DynamicRatePolicy dynamicRatePolicy,
            FailureRecorderPort failureRecorder) {
        this.fleetRatePort = Objects.requireNonNull(fleetRatePort, "fleetRatePort");
        this.financialParametersRepository = Objects.requireNonNull(financialParametersRepository,
                "financialParametersRepository");
        this.dynamicRatePolicy = Objects.requireNonNull(dynamicRatePolicy, "dynamicRatePolicy");
        this.failureRecorder = Objects.requireNonNull(failureRecorder, "failureRecorder");
    }

    @Override
    public List<BaseRateResult> provideBaseRates(ProvideBaseRateCommand command) {
        Objects.requireNonNull(command, "command");
        LocalDate evaluatedDate = Objects.requireNonNull(command.evaluatedDate(), "evaluatedDate");
        List<BoatId> requestedBoatIds = Objects.requireNonNull(command.boatIds(), "boatIds");

        Set<BoatId> uniqueBoatIds = new LinkedHashSet<>();
        for (BoatId boatId : requestedBoatIds) {
            uniqueBoatIds.add(Objects.requireNonNull(boatId, "boatId"));
        }
        if (uniqueBoatIds.isEmpty()) {
            return List.of();
        }

        FinancialParameters parameters = null;
        if (dynamicRatePolicy.requiresAdjustment(evaluatedDate)) {
            parameters = financialParametersRepository.findCurrent().orElse(null);
            if (parameters == null) {
                recordFailure(BaseRateFailureReason.DYNAMIC_RATE_NOT_CONFIGURED,
                        invocationPayload(evaluatedDate, uniqueBoatIds));
                throw new BaseRateException(BaseRateFailureReason.DYNAMIC_RATE_NOT_CONFIGURED,
                        "Financial parameters are required for the evaluated date but are not configured");
            }
        }

        Map<BoatId, BaseRate> baseRates;
        try {
            baseRates = fleetRatePort.findBaseRates(Collections.unmodifiableSet(uniqueBoatIds));
        } catch (BaseRateException exception) {
            recordFailure(exception.reason(), invocationPayload(evaluatedDate, uniqueBoatIds));
            throw exception;
        } catch (RuntimeException exception) {
            recordFailure(BaseRateFailureReason.FLEET_UNAVAILABLE,
                    invocationPayload(evaluatedDate, uniqueBoatIds));
            throw new BaseRateException(BaseRateFailureReason.FLEET_UNAVAILABLE,
                    "Fleet base rate query failed", exception);
        }
        if (baseRates == null) {
            recordFailure(BaseRateFailureReason.FLEET_UNAVAILABLE,
                    invocationPayload(evaluatedDate, uniqueBoatIds));
            throw new BaseRateException(BaseRateFailureReason.FLEET_UNAVAILABLE,
                    "Fleet returned no base rate result");
        }

        List<BaseRateResult> results = new ArrayList<>();
        UUID parametersId = parameters != null ? parameters.id() : null;
        for (BoatId boatId : uniqueBoatIds) {
            BaseRate baseRate = baseRates.get(boatId);
            if (baseRate == null) {
                recordFailure(BaseRateFailureReason.BASE_RATE_NOT_AVAILABLE,
                        boatPayload(evaluatedDate, boatId));
                continue;
            }
            results.add(new BaseRateResult(boatId,
                    dynamicRatePolicy.apply(baseRate, evaluatedDate, parameters),
                    parametersId));
        }
        return List.copyOf(results);
    }

    private void recordFailure(BaseRateFailureReason reason, String payloadRef) {
        failureRecorder.record(USE_CASE, null, reason.name(), payloadRef);
    }

    private static String invocationPayload(LocalDate evaluatedDate, Set<BoatId> boatIds) {
        String ids = boatIds.stream()
                .map(boatId -> boatId.value().toString())
                .collect(Collectors.joining(","));
        return "evaluatedDate=" + evaluatedDate + ";boatIds=[" + ids + "]";
    }

    private static String boatPayload(LocalDate evaluatedDate, BoatId boatId) {
        return "evaluatedDate=" + evaluatedDate + ";boatId=" + boatId.value();
    }
}
