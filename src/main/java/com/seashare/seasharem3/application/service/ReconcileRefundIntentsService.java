package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.RefundGatewayResult;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.ReconcileRefundIntentsUseCase;
import com.seashare.seasharem3.application.port.in.RegisterRefundResultUseCase;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.application.port.out.RefundGatewayPort;
import com.seashare.seasharem3.application.port.out.RefundCommandPublisher;
import com.seashare.seasharem3.application.port.out.RefundIntentRepository;
import com.seashare.seasharem3.application.port.out.RefundRecordRepository;
import com.seashare.seasharem3.domain.exception.GatewayCommunicationException;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.model.RefundIntent;
import com.seashare.seasharem3.domain.valueobject.RefundIntentStatus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Concilia reembolsos sin resultado definitivo consultando la pasarela por la referencia externa.
 * Un timeout o una consulta fallida dejan la intencion conciliable; nunca se asume exito (CE-004).
 */
public class ReconcileRefundIntentsService implements ReconcileRefundIntentsUseCase {

    private static final String USE_CASE = "UC09";
    private static final List<RefundIntentStatus> RECONCILABLE = List.of(
            RefundIntentStatus.PENDING_SEND, RefundIntentStatus.IN_PROCESS,
            RefundIntentStatus.COMMUNICATION_ERROR, RefundIntentStatus.COMPLETED);

    private final RefundIntentRepository refundIntentRepository;
    private final RefundRecordRepository refundRecordRepository;
    private final ChargeIntentRepository chargeIntentRepository;
    private final RefundGatewayPort refundGatewayPort;
    private final RegisterRefundResultUseCase registerRefundResult;
    private final FailureRecorderPort failureRecorder;
    private final RefundCommandPublisher refundCommandPublisher;

    public ReconcileRefundIntentsService(RefundIntentRepository refundIntentRepository,
            RefundRecordRepository refundRecordRepository, ChargeIntentRepository chargeIntentRepository,
            RefundGatewayPort refundGatewayPort, RegisterRefundResultUseCase registerRefundResult,
            FailureRecorderPort failureRecorder) {
        this(refundIntentRepository, refundRecordRepository, chargeIntentRepository, refundGatewayPort,
                registerRefundResult, failureRecorder, id -> {
                });
    }

    public ReconcileRefundIntentsService(RefundIntentRepository refundIntentRepository,
            RefundRecordRepository refundRecordRepository, ChargeIntentRepository chargeIntentRepository,
            RefundGatewayPort refundGatewayPort, RegisterRefundResultUseCase registerRefundResult,
            FailureRecorderPort failureRecorder, RefundCommandPublisher refundCommandPublisher) {
        this.refundIntentRepository = Objects.requireNonNull(refundIntentRepository, "refundIntentRepository");
        this.refundRecordRepository = Objects.requireNonNull(refundRecordRepository, "refundRecordRepository");
        this.chargeIntentRepository = Objects.requireNonNull(chargeIntentRepository, "chargeIntentRepository");
        this.refundGatewayPort = Objects.requireNonNull(refundGatewayPort, "refundGatewayPort");
        this.registerRefundResult = Objects.requireNonNull(registerRefundResult, "registerRefundResult");
        this.failureRecorder = Objects.requireNonNull(failureRecorder, "failureRecorder");
        this.refundCommandPublisher = Objects.requireNonNull(refundCommandPublisher, "refundCommandPublisher");
    }

    @Override
    public void reconcile() {
        for (RefundIntent intent : refundIntentRepository.findByStatusIn(RECONCILABLE)) {
            if (isAlreadyAudited(intent)) {
                continue;
            }
            reconcile(intent);
        }
    }

    private boolean isAlreadyAudited(RefundIntent intent) {
        return intent.status() == RefundIntentStatus.COMPLETED
                && refundRecordRepository.findByRefundIntentId(intent.id()).isPresent();
    }

    private void reconcile(RefundIntent intent) {
        if (intent.externalReference() == null || intent.externalReference().isBlank()) {
            if (intent.status() == RefundIntentStatus.PENDING_SEND) {
                refundCommandPublisher.publish(intent.id());
                return;
            }
            recordOnce(intent, "The refund was never confirmed by the payment gateway");
            return;
        }
        Optional<ChargeIntent> charge = chargeIntentRepository.findById(intent.chargeIntentId());
        if (charge.isEmpty() || charge.get().externalReference() == null) {
            recordOnce(intent, "The original charge has no external reference");
            return;
        }
        try {
            Optional<RefundGatewayResult> result = refundGatewayPort.fetchRefund(
                    charge.get().externalReference(), intent.externalReference());
            if (result.isEmpty()) {
                recordOnce(intent, "The payment gateway does not know the refund " + intent.externalReference());
                return;
            }
            registerRefundResult.register(result.get());
        } catch (GatewayCommunicationException exception) {
            recordOnce(intent, exception.getMessage());
        }
    }

    private void recordOnce(RefundIntent intent, String detail) {
        if (detail.equals(intent.statusDetail())) {
            return;
        }
        refundIntentRepository.save(intent.withStatusDetail(detail));
        failureRecorder.record(USE_CASE, intent.reservationId().value(), ErrorCode.INTERNAL_ERROR.name(),
                "refundIntentId=" + intent.id() + ";status=" + intent.status());
    }
}
