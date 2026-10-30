package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.GatewaySettlementRequest;
import com.seashare.seasharem3.application.dto.GatewaySettlementResult;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.RegisterSettlementResultUseCase;
import com.seashare.seasharem3.application.port.in.SendSettlementToGatewayUseCase;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.application.port.out.SettlementGatewayPort;
import com.seashare.seasharem3.application.port.out.SettlementIntentRepository;
import com.seashare.seasharem3.domain.exception.GatewayBusinessRejectionException;
import com.seashare.seasharem3.domain.exception.GatewayCommunicationException;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.model.SettlementIntent;
import com.seashare.seasharem3.domain.valueobject.SettlementStatus;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Envia una dispersion a la pasarela desde el worker y registra su referencia externa. */
public class SendSettlementToGatewayService implements SendSettlementToGatewayUseCase {

    private static final String USE_CASE = "UC10";

    private final SettlementIntentRepository settlementIntentRepository;
    private final ChargeIntentRepository chargeIntentRepository;
    private final SettlementGatewayPort settlementGatewayPort;
    private final RegisterSettlementResultUseCase registerSettlementResult;
    private final FailureRecorderPort failureRecorder;
    private final long collectorId;

    public SendSettlementToGatewayService(SettlementIntentRepository settlementIntentRepository,
            ChargeIntentRepository chargeIntentRepository, SettlementGatewayPort settlementGatewayPort,
            RegisterSettlementResultUseCase registerSettlementResult, FailureRecorderPort failureRecorder,
            long collectorId) {
        this.settlementIntentRepository = Objects.requireNonNull(settlementIntentRepository,
                "settlementIntentRepository");
        this.chargeIntentRepository = Objects.requireNonNull(chargeIntentRepository, "chargeIntentRepository");
        this.settlementGatewayPort = Objects.requireNonNull(settlementGatewayPort, "settlementGatewayPort");
        this.registerSettlementResult = Objects.requireNonNull(registerSettlementResult, "registerSettlementResult");
        this.failureRecorder = Objects.requireNonNull(failureRecorder, "failureRecorder");
        this.collectorId = collectorId;
    }

    @Override
    public void send(UUID settlementIntentId) {
        Objects.requireNonNull(settlementIntentId, "settlementIntentId");
        Optional<SettlementIntent> found = settlementIntentRepository.findByIdForUpdate(settlementIntentId);
        if (found.isEmpty()) {
            failureRecorder.record(USE_CASE, null, ErrorCode.SETTLEMENT_INTENT_NOT_FOUND.name(),
                    "settlementIntentId=" + settlementIntentId);
            return;
        }
        SettlementIntent intent = found.get();
        if (intent.isTerminal()) {
            return;
        }
        if (collectorId <= 0) {
            failureRecorder.record(USE_CASE, intent.reservationId().value(), ErrorCode.INTERNAL_ERROR.name(),
                    "seashare.gateway.collector-id is not configured");
            settlementIntentRepository.save(intent.transitionTo(SettlementStatus.REJECTED)
                    .withStatusDetail("seashare.gateway.collector-id is not configured"));
            return;
        }

        Optional<ChargeIntent> charge = chargeIntentRepository.findById(intent.chargeIntentId());
        if (charge.isEmpty() || charge.get().externalReference() == null
                || charge.get().externalReference().isBlank()) {
            settlementIntentRepository.save(intent.transitionTo(SettlementStatus.REJECTED)
                    .withStatusDetail("The original charge has no external reference"));
            return;
        }

        GatewaySettlementResult result;
        try {
            result = settlementGatewayPort.send(toRequest(intent, charge.get()));
        } catch (GatewayBusinessRejectionException exception) {
            settlementIntentRepository.save(intent.transitionTo(SettlementStatus.REJECTED)
                    .withStatusDetail(exception.getMessage()));
            return;
        } catch (GatewayCommunicationException exception) {
            settlementIntentRepository.save(intent.transitionTo(SettlementStatus.COMMUNICATION_ERROR)
                    .withStatusDetail(exception.getMessage()));
            throw exception;
        }

        if (result.externalId() == null || result.externalId().isBlank()) {
            failureRecorder.record(USE_CASE, intent.reservationId().value(), ErrorCode.INTERNAL_ERROR.name(),
                    "settlementIntentId=" + intent.id() + ";status=" + result.status());
            return;
        }
        settlementIntentRepository.save(intent.withExternalReference(result.externalId()));
        registerSettlementResult.register(result);
    }

    private GatewaySettlementRequest toRequest(SettlementIntent intent, ChargeIntent charge) {
        return new GatewaySettlementRequest(collectorId, intent.reservationId().value(), intent.amount(),
                charge.externalReference(), intent.idempotencyKey().value());
    }
}
