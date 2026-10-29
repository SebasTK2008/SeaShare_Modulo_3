package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.ChargeCommand;
import com.seashare.seasharem3.application.dto.ChargeGatewayRequest;
import com.seashare.seasharem3.application.dto.ChargeGatewayResult;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.RegisterChargeResultUseCase;
import com.seashare.seasharem3.application.port.in.SendChargeToGatewayUseCase;
import com.seashare.seasharem3.application.port.out.ChargeGatewayPort;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.domain.exception.GatewayCommunicationException;
import com.seashare.seasharem3.domain.exception.InvalidPaymentTokenException;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.valueobject.ChargeIntentStatus;

import java.util.Objects;

/**
 * Orquesta UC05/HU1 desde la cola interna: envia el cobro a la pasarela y conserva el resultado
 * sobre la intencion.
 *
 * <p>El envio usa la clave idempotente del intento, de modo que un reintento tecnico no genera un
 * cobro doble (SPEC RNF-003; general-plan D-33). El token del medio de pago se lee de la propia
 * intencion: el mensaje de la cola no transporta credenciales de pago (regla 7, CE-005).</p>
 *
 * <p>El resultado se clasifica segun el contrato de la pasarela (regla 5): un {@code 400} de
 * validacion del token no es reintentable y deja la intencion en {@code REJECTED} con el detalle
 * tecnico, sin registro de cobro; un timeout, un {@code 502} o un {@code 503} dejan la intencion en
 * {@code COMMUNICATION_ERROR} y la excepcion se propaga para que la cola reintente con la misma
 * clave, porque un timeout no permite concluir que el cobro fallo (SPEC RNF-003).</p>
 *
 * <p>El metodo no es transaccional a proposito: la llamada a la pasarela no debe formar parte de una
 * transaccion de base de datos y el estado {@code COMMUNICATION_ERROR} debe sobrevivir al
 * relanzamiento que dispara el reintento de la cola.</p>
 *
 * <p>Fuente: UC05 plan T014 y T015, reglas 4, 5 y 7; SPEC RF-003, RF-011, RF-013 y CE-003.</p>
 */
public class SendChargeToGatewayService implements SendChargeToGatewayUseCase {

    private static final String USE_CASE = "UC05";
    private static final int INSTALLMENTS = 1;

    private final ChargeIntentRepository chargeIntentRepository;
    private final ChargeGatewayPort chargeGatewayPort;
    private final RegisterChargeResultUseCase registerChargeResult;
    private final FailureRecorderPort failureRecorder;

    public SendChargeToGatewayService(ChargeIntentRepository chargeIntentRepository,
            ChargeGatewayPort chargeGatewayPort, RegisterChargeResultUseCase registerChargeResult,
            FailureRecorderPort failureRecorder) {
        this.chargeIntentRepository = Objects.requireNonNull(chargeIntentRepository, "chargeIntentRepository");
        this.chargeGatewayPort = Objects.requireNonNull(chargeGatewayPort, "chargeGatewayPort");
        this.registerChargeResult = Objects.requireNonNull(registerChargeResult, "registerChargeResult");
        this.failureRecorder = Objects.requireNonNull(failureRecorder, "failureRecorder");
    }

    @Override
    public void send(ChargeCommand command) {
        Objects.requireNonNull(command, "command");

        ChargeIntent intent = chargeIntentRepository.findById(command.chargeIntentId()).orElse(null);
        if (intent == null) {
            failureRecorder.record(USE_CASE, null, ErrorCode.CHARGE_INTENT_NOT_FOUND.name(),
                    "chargeIntentId=" + command.chargeIntentId());
            return;
        }
        if (intent.isTerminal()) {
            return;
        }

        String incompleteDetail = incompleteRequestDetail(intent, command.payerEmail());
        if (incompleteDetail != null) {
            chargeIntentRepository.save(intent.transitionTo(ChargeIntentStatus.REJECTED)
                    .withStatusDetail(incompleteDetail));
            return;
        }

        ChargeGatewayResult result;
        try {
            result = chargeGatewayPort.send(toRequest(intent, command.payerEmail()));
        } catch (InvalidPaymentTokenException exception) {
            chargeIntentRepository.save(intent.transitionTo(ChargeIntentStatus.REJECTED)
                    .withStatusDetail(exception.getMessage()));
            return;
        } catch (GatewayCommunicationException exception) {
            chargeIntentRepository.save(intent.transitionTo(ChargeIntentStatus.COMMUNICATION_ERROR)
                    .withStatusDetail(exception.getMessage()));
            throw exception;
        }

        String externalId = result.externalId();
        if (externalId == null || externalId.isBlank()) {
            failureRecorder.record(USE_CASE, intent.reservationId().value(), ErrorCode.INTERNAL_ERROR.name(),
                    "chargeIntentId=" + intent.id() + ";status=" + result.status());
            return;
        }
        chargeIntentRepository.save(intent.withExternalReference(externalId));
        registerChargeResult.register(result);
    }

    /**
     * Construye la solicitud con un unico monto (el valor total de la intencion) y la clave
     * idempotente del intento como cabecera {@code X-Idempotency-Key} (reglas 4 y 7).
     */
    private static ChargeGatewayRequest toRequest(ChargeIntent intent, String payerEmail) {
        return new ChargeGatewayRequest(intent.amount(), intent.paymentMethod().paymentTokenRef(),
                "Reserva " + intent.reservationId().value(), INSTALLMENTS,
                intent.paymentMethod().paymentMethodType(), payerEmail,
                intent.reservationId().value().toString(), intent.idempotencyKey().value());
    }

    /**
     * El contrato de la pasarela exige tipo de medio y correo del pagador: sin ellos no se envia un
     * cobro con datos incompletos y la intencion queda rechazada con el detalle tecnico, sin
     * marcar la reserva como cobrada.
     */
    private static String incompleteRequestDetail(ChargeIntent intent, String payerEmail) {
        if (intent.paymentMethod().paymentMethodType() == null
                || intent.paymentMethod().paymentMethodType().isBlank()) {
            return "The payment method type is required to charge the reservation";
        }
        if (payerEmail == null || payerEmail.isBlank()) {
            return "The payer email is required to charge the reservation";
        }
        return null;
    }
}
