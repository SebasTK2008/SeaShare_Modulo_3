package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.ChargeGatewayResult;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.ReconcileChargeIntentsUseCase;
import com.seashare.seasharem3.application.port.in.RegisterChargeResultUseCase;
import com.seashare.seasharem3.application.port.out.ChargeGatewayPort;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.application.port.out.ChargeRecordQueryPort;
import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.domain.exception.GatewayCommunicationException;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.valueobject.ChargeIntentStatus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Concilia las intenciones de cobro sin resultado definitivo (SPEC RNF-003).
 *
 * <p>Cubre todo intento que puede haber quedado a medias: el que nunca salio ({@code PENDING_SEND}),
 * el que la pasarela esta procesando ({@code IN_PROCESS}), el que fallo la comunicacion
 * ({@code COMMUNICATION_ERROR}) y el que quedo capturado sin registro de auditoria
 * ({@code CAPTURED}), que se vuelve a pasar por el servicio de registro para crear el registro
 * que falta (RF-006, CE-002).</p>
 *
 * <p>La conciliacion consulta a la pasarela el estado real del pago por su referencia externa y
 * converge en el mismo servicio que registra el resultado, de modo que un cobro nunca queda en un
 * estado indefinido (CE-003). Un {@code 404} de la pasarela no es un veredicto financiero: la
 * intencion no se cierra y el evento queda registrado para revision.</p>
 *
 * <p>La intencion conserva la clave idempotente del intento, de modo que un reintento tecnico
 * posterior no genera un cobro doble (general-plan D-33).</p>
 *
 * <p>Fuente: UC05 plan T023 y T024; SPEC RNF-003, RF-006, CE-002 y CE-003.</p>
 */
public class ReconcileChargeIntentsService implements ReconcileChargeIntentsUseCase {

    private static final String USE_CASE = "UC05";

    private static final List<ChargeIntentStatus> RECONCILABLE_STATUSES = List.of(
            ChargeIntentStatus.PENDING_SEND, ChargeIntentStatus.IN_PROCESS,
            ChargeIntentStatus.COMMUNICATION_ERROR, ChargeIntentStatus.CAPTURED);

    private final ChargeIntentRepository chargeIntentRepository;
    private final ChargeRecordQueryPort chargeRecordQueryPort;
    private final ChargeGatewayPort chargeGatewayPort;
    private final RegisterChargeResultUseCase registerChargeResult;
    private final FailureRecorderPort failureRecorder;

    public ReconcileChargeIntentsService(ChargeIntentRepository chargeIntentRepository,
            ChargeRecordQueryPort chargeRecordQueryPort, ChargeGatewayPort chargeGatewayPort,
            RegisterChargeResultUseCase registerChargeResult, FailureRecorderPort failureRecorder) {
        this.chargeIntentRepository = Objects.requireNonNull(chargeIntentRepository, "chargeIntentRepository");
        this.chargeRecordQueryPort = Objects.requireNonNull(chargeRecordQueryPort, "chargeRecordQueryPort");
        this.chargeGatewayPort = Objects.requireNonNull(chargeGatewayPort, "chargeGatewayPort");
        this.registerChargeResult = Objects.requireNonNull(registerChargeResult, "registerChargeResult");
        this.failureRecorder = Objects.requireNonNull(failureRecorder, "failureRecorder");
    }

    @Override
    public void reconcile() {
        for (ChargeIntent intent : chargeIntentRepository.findByStatusIn(RECONCILABLE_STATUSES)) {
            if (isAlreadyAudited(intent)) {
                continue;
            }
            reconcile(intent);
        }
    }

    /** El cobro capturado que ya tiene su registro de auditoria no necesita reconciliarse. */
    private boolean isAlreadyAudited(ChargeIntent intent) {
        return intent.status() == ChargeIntentStatus.CAPTURED
                && chargeRecordQueryPort.findByChargeIntentId(intent.id()).isPresent();
    }

    private void reconcile(ChargeIntent intent) {
        String externalReference = intent.externalReference();
        if (externalReference == null || externalReference.isBlank()) {
            recordOnce(intent, "The charge was never confirmed by the payment gateway");
            return;
        }
        try {
            Optional<ChargeGatewayResult> result = chargeGatewayPort.fetchPayment(externalReference);
            if (result.isEmpty()) {
                recordOnce(intent, "The payment gateway does not know the payment " + externalReference);
                return;
            }
            registerChargeResult.register(result.get());
        } catch (GatewayCommunicationException exception) {
            recordOnce(intent, exception.getMessage());
        }
    }

    /**
     * Registra el fallo una sola vez por intencion: el detalle queda en la intencion y solo se anota
     * en {@code operational_failure} cuando cambia, para no repetir la fila en cada ciclo.
     */
    private void recordOnce(ChargeIntent intent, String detail) {
        if (detail.equals(intent.statusDetail())) {
            return;
        }
        chargeIntentRepository.save(intent.withStatusDetail(detail));
        failureRecorder.record(USE_CASE, intent.reservationId().value(), ErrorCode.INTERNAL_ERROR.name(),
                "chargeIntentId=" + intent.id() + ";status=" + intent.status());
    }
}
