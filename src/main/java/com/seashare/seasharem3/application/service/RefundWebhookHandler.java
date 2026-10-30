package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.GatewayWebhookNotification;
import com.seashare.seasharem3.application.dto.RefundGatewayResult;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.GatewayWebhookHandler;
import com.seashare.seasharem3.application.port.in.RegisterRefundResultUseCase;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.application.port.out.RefundGatewayPort;
import com.seashare.seasharem3.application.port.out.RefundIntentRepository;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.model.RefundIntent;

import java.util.Objects;
import java.util.Optional;

/**
 * Manejador del webhook {@code type=refund}: asocia la notificacion por external_reference,
 * consulta el detalle del reembolso en el pago original y converge en el registro del resultado.
 */
public class RefundWebhookHandler implements GatewayWebhookHandler {

    private static final String USE_CASE = "UC09";
    private static final String SUPPORTED_TYPE = "refund";

    private final RefundIntentRepository refundIntentRepository;
    private final ChargeIntentRepository chargeIntentRepository;
    private final RefundGatewayPort refundGatewayPort;
    private final RegisterRefundResultUseCase registerRefundResult;
    private final FailureRecorderPort failureRecorder;

    public RefundWebhookHandler(RefundIntentRepository refundIntentRepository,
            ChargeIntentRepository chargeIntentRepository, RefundGatewayPort refundGatewayPort,
            RegisterRefundResultUseCase registerRefundResult, FailureRecorderPort failureRecorder) {
        this.refundIntentRepository = Objects.requireNonNull(refundIntentRepository, "refundIntentRepository");
        this.chargeIntentRepository = Objects.requireNonNull(chargeIntentRepository, "chargeIntentRepository");
        this.refundGatewayPort = Objects.requireNonNull(refundGatewayPort, "refundGatewayPort");
        this.registerRefundResult = Objects.requireNonNull(registerRefundResult, "registerRefundResult");
        this.failureRecorder = Objects.requireNonNull(failureRecorder, "failureRecorder");
    }

    @Override
    public String supportedType() {
        return SUPPORTED_TYPE;
    }

    @Override
    public void handle(GatewayWebhookNotification notification) {
        Objects.requireNonNull(notification, "notification");
        if (notification.dataId() == null || notification.dataId().isBlank()) {
            failureRecorder.record(USE_CASE, null, ErrorCode.VALIDATION_ERROR.name(), payloadRef(notification));
            return;
        }
        Optional<RefundIntent> intent = refundIntentRepository.findByExternalReference(notification.dataId());
        if (intent.isEmpty()) {
            failureRecorder.record(USE_CASE, null, ErrorCode.REFUND_INTENT_NOT_FOUND.name(),
                    payloadRef(notification));
            return;
        }
        Optional<ChargeIntent> charge = chargeIntentRepository.findById(intent.get().chargeIntentId());
        if (charge.isEmpty() || charge.get().externalReference() == null) {
            failureRecorder.record(USE_CASE, intent.get().reservationId().value(),
                    ErrorCode.CHARGE_INTENT_NOT_FOUND.name(), payloadRef(notification));
            return;
        }
        Optional<RefundGatewayResult> result = refundGatewayPort.fetchRefund(
                charge.get().externalReference(), notification.dataId());
        if (result.isEmpty()) {
            failureRecorder.record(USE_CASE, intent.get().reservationId().value(), ErrorCode.INTERNAL_ERROR.name(),
                    payloadRef(notification));
            return;
        }
        registerRefundResult.register(result.get());
    }

    private static String payloadRef(GatewayWebhookNotification notification) {
        return "type=" + notification.type() + ";action=" + notification.action() + ";dataId="
                + notification.dataId();
    }
}
