package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.GatewayWebhookNotification;
import com.seashare.seasharem3.application.dto.RefundGatewayResult;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.GatewayWebhookHandler;
import com.seashare.seasharem3.application.port.in.RegisterRefundResultUseCase;
import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.application.port.out.RefundGatewayPort;
import com.seashare.seasharem3.application.port.out.RefundIntentRepository;

import java.util.List;
import java.util.Objects;

/**
 * Manejador del webhook {@code type=payment} para UC09: Mercado Pago notifica cambios del pago y
 * UC09 consulta la lista de reembolsos de ese pago para converger en el registro del resultado.
 *
 * <p>El {@code data.id} de la notificacion es el identificador del pago (la referencia externa del
 * cobro). Cada reembolso se asocia con su intencion local por {@code refund.id ==
 * refund_intent.external_reference}; los reembolsos sin intencion local se ignoran porque pueden
 * pertenecer a otra integracion o a un pago aun no confirmado. Asi el handler comparte el topic
 * {@code payment} con UC05 sin depender de un topic inexistente (D-UC09-10).</p>
 *
 * <p>Fuente: UC09 plan T021; contrato {@code pasarela-webhook-resultados.md} §3.4 y §6.</p>
 */
public class RefundWebhookHandler implements GatewayWebhookHandler {

    private static final String USE_CASE = "UC09";
    private static final String SUPPORTED_TYPE = "payment";

    private final RefundIntentRepository refundIntentRepository;
    private final RefundGatewayPort refundGatewayPort;
    private final RegisterRefundResultUseCase registerRefundResult;
    private final FailureRecorderPort failureRecorder;

    public RefundWebhookHandler(RefundIntentRepository refundIntentRepository,
            RefundGatewayPort refundGatewayPort, RegisterRefundResultUseCase registerRefundResult,
            FailureRecorderPort failureRecorder) {
        this.refundIntentRepository = Objects.requireNonNull(refundIntentRepository, "refundIntentRepository");
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

        List<RefundGatewayResult> refunds = refundGatewayPort.fetchRefunds(notification.dataId());
        for (RefundGatewayResult refund : refunds) {
            if (refund.externalId() == null || refund.externalId().isBlank()) {
                continue;
            }
            if (refundIntentRepository.findByExternalReference(refund.externalId()).isEmpty()) {
                // Reembolso ajeno a esta integracion o aun no confirmado localmente: no es un fallo.
                continue;
            }
            registerRefundResult.register(refund);
        }
    }

    private static String payloadRef(GatewayWebhookNotification notification) {
        return "type=" + notification.type() + ";action=" + notification.action() + ";dataId="
                + notification.dataId();
    }
}
