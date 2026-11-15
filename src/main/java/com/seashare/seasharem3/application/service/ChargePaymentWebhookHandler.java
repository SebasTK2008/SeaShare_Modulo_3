package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.ChargeGatewayResult;
import com.seashare.seasharem3.application.dto.GatewayWebhookNotification;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.GatewayWebhookHandler;
import com.seashare.seasharem3.application.port.in.RegisterChargeResultUseCase;
import com.seashare.seasharem3.application.port.out.ChargeGatewayPort;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.domain.model.ChargeIntent;

import java.util.Objects;
import java.util.Optional;

/**
 * Manejador del webhook de la pasarela para el tipo {@code payment} (UC05).
 *
 * <p>Asocia la notificacion con su intencion exclusivamente por
 * {@code external_reference == dataId}; si no hay intencion asociada, registra el evento en
 * {@code operational_failure} y confirma la recepcion sin crear registros inmutables. Con la
 * intencion localizada, consulta el estado real del pago a la pasarela y converge en el mismo
 * servicio que registra el resultado.</p>
 *
 * <p>La deduplicacion la da la asociacion por referencia externa: dos notificaciones de la misma
 * operacion actualizan la misma intencion y el registro de auditoria se crea una sola vez.</p>
 *
 * <p>Fuente: UC05 plan T020; contrato {@code pasarela-webhook-resultados.md} §3.4, §3.5 y §6.</p>
 */
public class ChargePaymentWebhookHandler implements GatewayWebhookHandler {

    private static final String USE_CASE = "UC05";
    private static final String SUPPORTED_TYPE = "payment";

    private final ChargeIntentRepository chargeIntentRepository;
    private final ChargeGatewayPort chargeGatewayPort;
    private final RegisterChargeResultUseCase registerChargeResult;
    private final FailureRecorderPort failureRecorder;

    public ChargePaymentWebhookHandler(ChargeIntentRepository chargeIntentRepository,
            ChargeGatewayPort chargeGatewayPort, RegisterChargeResultUseCase registerChargeResult,
            FailureRecorderPort failureRecorder) {
        this.chargeIntentRepository = Objects.requireNonNull(chargeIntentRepository, "chargeIntentRepository");
        this.chargeGatewayPort = Objects.requireNonNull(chargeGatewayPort, "chargeGatewayPort");
        this.registerChargeResult = Objects.requireNonNull(registerChargeResult, "registerChargeResult");
        this.failureRecorder = Objects.requireNonNull(failureRecorder, "failureRecorder");
    }

    @Override
    public String supportedType() {
        return SUPPORTED_TYPE;
    }

    @Override
    public void handle(GatewayWebhookNotification notification) {
        Objects.requireNonNull(notification, "notification");

        ChargeIntent intent = findIntent(notification.dataId());
        if (intent == null) {
            failureRecorder.record(USE_CASE, null, ErrorCode.CHARGE_INTENT_NOT_FOUND.name(),
                    payloadRef(notification));
            return;
        }
        Optional<ChargeGatewayResult> result = chargeGatewayPort.fetchPayment(notification.dataId());
        if (result.isEmpty()) {
            // Un 404 de la pasarela no es un veredicto financiero: la intencion no se cierra y el
            // evento queda registrado para revision (contrato §3.6).
            failureRecorder.record(USE_CASE, intent.reservationId().value(), ErrorCode.INTERNAL_ERROR.name(),
                    payloadRef(notification));
            return;
        }
        registerChargeResult.register(result.get());
    }

    private ChargeIntent findIntent(String dataId) {
        if (dataId == null || dataId.isBlank()) {
            return null;
        }
        return chargeIntentRepository.findByExternalReference(dataId).orElse(null);
    }

    private static String payloadRef(GatewayWebhookNotification notification) {
        return "type=" + notification.type() + ";action=" + notification.action() + ";dataId=" + notification.dataId();
    }
}
