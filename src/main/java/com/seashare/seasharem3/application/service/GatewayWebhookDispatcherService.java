package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.GatewayWebhookNotification;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.DispatchGatewayNotificationUseCase;
import com.seashare.seasharem3.application.port.in.GatewayWebhookHandler;
import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.application.port.out.GatewayNotificationPublisher;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

/**
 * Despacha por {@code type} la notificacion del webhook de la pasarela.
 *
 * <p>Si hay un manejador para el tipo, la notificacion se persiste de forma durable (outbox) y el
 * procesamiento ocurre despues, en el worker de la cola interna: asi la pasarela recibe el acuse
 * de recibo sin esperar al resultado (contrato §3.2). Si no hay manejador para el tipo, el evento
 * se registra en {@code operational_failure} y se confirma la recepcion sin reintentos, para no
 * provocar reintentos infinitos de la pasarela (contrato §3.6).</p>
 *
 * <p>El metodo es transaccional porque la escritura en el outbox es obligatoria dentro de la
 * transaccion del llamador: la notificacion solo se considera durable si el outbox confirma la
 * escritura.</p>
 *
 * <p>Fuente: UC05 plan T019; contrato {@code pasarela-webhook-resultados.md} §3.4 y §3.6.</p>
 */
public class GatewayWebhookDispatcherService implements DispatchGatewayNotificationUseCase {

    private static final String USE_CASE = "UC05";

    private final List<GatewayWebhookHandler> handlers;
    private final GatewayNotificationPublisher notificationPublisher;
    private final FailureRecorderPort failureRecorder;

    public GatewayWebhookDispatcherService(List<GatewayWebhookHandler> handlers,
            GatewayNotificationPublisher notificationPublisher, FailureRecorderPort failureRecorder) {
        this.handlers = List.copyOf(handlers);
        this.notificationPublisher = Objects.requireNonNull(notificationPublisher, "notificationPublisher");
        this.failureRecorder = Objects.requireNonNull(failureRecorder, "failureRecorder");
    }

    @Override
    @Transactional
    public void dispatch(GatewayWebhookNotification notification) {
        Objects.requireNonNull(notification, "notification");

        Optional<GatewayWebhookHandler> handler = handlers.stream()
                .filter(candidate -> candidate.supportedType().equals(notification.type()))
                .findFirst();
        if (handler.isEmpty()) {
            failureRecorder.record(USE_CASE, null, ErrorCode.INTERNAL_ERROR.name(), payloadRef(notification));
            return;
        }
        notificationPublisher.publish(notification);
    }

    private static String payloadRef(GatewayWebhookNotification notification) {
        return "type=" + notification.type() + ";action=" + notification.action() + ";dataId=" + notification.dataId();
    }
}
