package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.GatewaySettlementResult;
import com.seashare.seasharem3.application.dto.GatewayWebhookNotification;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.GatewayWebhookHandler;
import com.seashare.seasharem3.application.port.in.RegisterSettlementResultUseCase;
import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.application.port.out.SettlementGatewayPort;
import com.seashare.seasharem3.application.port.out.SettlementIntentRepository;
import com.seashare.seasharem3.domain.model.SettlementIntent;

import java.util.Objects;
import java.util.Optional;

/** Manejador del webhook {@code type=merchant_order} para UC10. */
public class SettlementWebhookHandler implements GatewayWebhookHandler {

    private static final String USE_CASE = "UC10";
    private static final String SUPPORTED_TYPE = "merchant_order";

    private final SettlementIntentRepository settlementIntentRepository;
    private final SettlementGatewayPort settlementGatewayPort;
    private final RegisterSettlementResultUseCase registerSettlementResult;
    private final FailureRecorderPort failureRecorder;

    public SettlementWebhookHandler(SettlementIntentRepository settlementIntentRepository,
            SettlementGatewayPort settlementGatewayPort, RegisterSettlementResultUseCase registerSettlementResult,
            FailureRecorderPort failureRecorder) {
        this.settlementIntentRepository = Objects.requireNonNull(settlementIntentRepository,
                "settlementIntentRepository");
        this.settlementGatewayPort = Objects.requireNonNull(settlementGatewayPort, "settlementGatewayPort");
        this.registerSettlementResult = Objects.requireNonNull(registerSettlementResult, "registerSettlementResult");
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
        Optional<SettlementIntent> intent = settlementIntentRepository
                .findByExternalReference(notification.dataId());
        if (intent.isEmpty()) {
            failureRecorder.record(USE_CASE, null, ErrorCode.SETTLEMENT_INTENT_NOT_FOUND.name(),
                    payloadRef(notification));
            return;
        }
        Optional<GatewaySettlementResult> result = settlementGatewayPort.fetch(notification.dataId());
        if (result.isEmpty()) {
            failureRecorder.record(USE_CASE, intent.get().reservationId().value(), ErrorCode.INTERNAL_ERROR.name(),
                    payloadRef(notification));
            return;
        }
        registerSettlementResult.register(result.get());
    }

    private static String payloadRef(GatewayWebhookNotification notification) {
        return "type=" + notification.type() + ";action=" + notification.action() + ";dataId="
                + notification.dataId();
    }
}
