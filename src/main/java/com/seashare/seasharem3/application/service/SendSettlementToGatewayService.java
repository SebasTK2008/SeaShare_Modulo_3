package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.GatewaySettlementRequest;
import com.seashare.seasharem3.application.dto.GatewaySettlementResult;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.RegisterSettlementResultUseCase;
import com.seashare.seasharem3.application.port.in.SendSettlementToGatewayUseCase;
import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.application.port.out.SettlementGatewayPort;
import com.seashare.seasharem3.application.port.out.SettlementIntentRepository;
import com.seashare.seasharem3.domain.exception.GatewayBusinessRejectionException;
import com.seashare.seasharem3.domain.exception.GatewayCommunicationException;
import com.seashare.seasharem3.domain.model.SettlementIntent;
import com.seashare.seasharem3.domain.valueobject.SettlementStatus;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Envia una liquidacion al libro interno simulado desde el worker y registra su referencia externa.
 *
 * <p>No depende del cobro original ni de una cuenta de vendedor: el libro interno no dispersa
 * fondos en Mercado Pago (D-UC10-10). La clave idempotente de la intencion se reutiliza en los
 * reintentos y produce la misma referencia simulada.</p>
 */
public class SendSettlementToGatewayService implements SendSettlementToGatewayUseCase {

    private static final String USE_CASE = "UC10";

    private final SettlementIntentRepository settlementIntentRepository;
    private final SettlementGatewayPort settlementGatewayPort;
    private final RegisterSettlementResultUseCase registerSettlementResult;
    private final FailureRecorderPort failureRecorder;

    public SendSettlementToGatewayService(SettlementIntentRepository settlementIntentRepository,
            SettlementGatewayPort settlementGatewayPort, RegisterSettlementResultUseCase registerSettlementResult,
            FailureRecorderPort failureRecorder) {
        this.settlementIntentRepository = Objects.requireNonNull(settlementIntentRepository,
                "settlementIntentRepository");
        this.settlementGatewayPort = Objects.requireNonNull(settlementGatewayPort, "settlementGatewayPort");
        this.registerSettlementResult = Objects.requireNonNull(registerSettlementResult, "registerSettlementResult");
        this.failureRecorder = Objects.requireNonNull(failureRecorder, "failureRecorder");
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

        GatewaySettlementResult result;
        try {
            result = settlementGatewayPort.send(toRequest(intent));
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
            GatewayCommunicationException exception = new GatewayCommunicationException(
                    "The settlement ledger returned no external reference");
            settlementIntentRepository.save(intent.transitionTo(SettlementStatus.COMMUNICATION_ERROR)
                    .withStatusDetail(exception.getMessage()));
            failureRecorder.record(USE_CASE, intent.reservationId().value(), ErrorCode.INTERNAL_ERROR.name(),
                    "settlementIntentId=" + intent.id() + ";status=" + result.status());
            throw exception;
        }
        settlementIntentRepository.save(intent.withExternalReference(result.externalId()));
        registerSettlementResult.register(result);
    }

    private static GatewaySettlementRequest toRequest(SettlementIntent intent) {
        return new GatewaySettlementRequest(intent.reservationId().value(), intent.amount(),
                intent.idempotencyKey().value());
    }
}
