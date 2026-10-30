package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.RefundGatewayRequest;
import com.seashare.seasharem3.application.dto.RefundGatewayResult;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.RegisterRefundResultUseCase;
import com.seashare.seasharem3.application.port.in.SendRefundToGatewayUseCase;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.application.port.out.RefundGatewayPort;
import com.seashare.seasharem3.application.port.out.RefundIntentRepository;
import com.seashare.seasharem3.domain.exception.GatewayBusinessRejectionException;
import com.seashare.seasharem3.domain.exception.GatewayCommunicationException;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.model.RefundIntent;
import com.seashare.seasharem3.domain.valueobject.RefundIntentStatus;
import com.seashare.seasharem3.domain.valueobject.RefundOperationType;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Envia el reembolso a la pasarela desde el worker. Persiste la referencia externa del reembolso
 * antes de registrar el resultado, para que el webhook asincrono pueda asociarlo.
 *
 * <p>No es transaccional a proposito: la llamada remota no debe formar parte de una transaccion y
 * el estado COMMUNICATION_ERROR debe sobrevivir al relanzamiento que dispara el reintento.</p>
 */
public class SendRefundToGatewayService implements SendRefundToGatewayUseCase {

    private static final String USE_CASE = "UC09";

    private final RefundIntentRepository refundIntentRepository;
    private final ChargeIntentRepository chargeIntentRepository;
    private final RefundGatewayPort refundGatewayPort;
    private final RegisterRefundResultUseCase registerRefundResult;
    private final FailureRecorderPort failureRecorder;

    public SendRefundToGatewayService(RefundIntentRepository refundIntentRepository,
            ChargeIntentRepository chargeIntentRepository, RefundGatewayPort refundGatewayPort,
            RegisterRefundResultUseCase registerRefundResult, FailureRecorderPort failureRecorder) {
        this.refundIntentRepository = Objects.requireNonNull(refundIntentRepository, "refundIntentRepository");
        this.chargeIntentRepository = Objects.requireNonNull(chargeIntentRepository, "chargeIntentRepository");
        this.refundGatewayPort = Objects.requireNonNull(refundGatewayPort, "refundGatewayPort");
        this.registerRefundResult = Objects.requireNonNull(registerRefundResult, "registerRefundResult");
        this.failureRecorder = Objects.requireNonNull(failureRecorder, "failureRecorder");
    }

    @Override
    public void send(UUID refundIntentId) {
        Objects.requireNonNull(refundIntentId, "refundIntentId");
        Optional<RefundIntent> found = refundIntentRepository.findByIdForUpdate(refundIntentId);
        if (found.isEmpty()) {
            failureRecorder.record(USE_CASE, null, ErrorCode.REFUND_INTENT_NOT_FOUND.name(),
                    "refundIntentId=" + refundIntentId);
            return;
        }
        RefundIntent intent = found.get();
        if (intent.isTerminal()) {
            return;
        }

        Optional<ChargeIntent> charge = chargeIntentRepository.findById(intent.chargeIntentId());
        if (charge.isEmpty() || charge.get().externalReference() == null
                || charge.get().externalReference().isBlank()) {
            refundIntentRepository.save(intent.transitionTo(RefundIntentStatus.REJECTED)
                    .withStatusDetail("The original charge has no external reference"));
            return;
        }

        RefundGatewayResult result;
        try {
            result = refundGatewayPort.send(toRequest(intent, charge.get()));
        } catch (GatewayBusinessRejectionException exception) {
            refundIntentRepository.save(intent.transitionTo(RefundIntentStatus.REJECTED)
                    .withStatusDetail(exception.getMessage()));
            return;
        } catch (GatewayCommunicationException exception) {
            refundIntentRepository.save(intent.transitionTo(RefundIntentStatus.COMMUNICATION_ERROR)
                    .withStatusDetail(exception.getMessage()));
            throw exception;
        }

        if (result.externalId() == null || result.externalId().isBlank()) {
            failureRecorder.record(USE_CASE, intent.reservationId().value(), ErrorCode.INTERNAL_ERROR.name(),
                    "refundIntentId=" + intent.id() + ";status=" + result.status());
            return;
        }
        refundIntentRepository.save(intent.withExternalReference(result.externalId()));
        registerRefundResult.register(result);
    }

    private static RefundGatewayRequest toRequest(RefundIntent intent, ChargeIntent charge) {
        return new RefundGatewayRequest(RefundOperationType.REFUND, charge.externalReference(),
                intent.reservationId().value(), intent.amount(), intent.idempotencyKey().value());
    }
}
