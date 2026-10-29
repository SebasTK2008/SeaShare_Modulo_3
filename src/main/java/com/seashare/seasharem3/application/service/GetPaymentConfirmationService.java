package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.PaymentConfirmationQuery;
import com.seashare.seasharem3.application.dto.PaymentConfirmationResult;
import com.seashare.seasharem3.application.port.in.GetPaymentConfirmationUseCase;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.domain.exception.ChargeIntentNotFoundException;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.valueobject.PaymentConfirmationStatus;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Consulta de solo lectura del estado vigente del cobro de una reserva.
 *
 * <p>Lee la intención más reciente creada por UC05 y la traduce al vocabulario de UC06. No contacta
 * a la pasarela ni escribe montos o estados: esas responsabilidades pertenecen a UC05, UC09 y UC10
 * [SPEC RF-002, RF-006; general-plan §3.4].</p>
 *
 * <p>El único monto disponible en {@code charge_intent} es {@code amount}; se informa como
 * {@code charged_amount} únicamente cuando el estado es {@code APPROVED}. Los montos autorizado,
 * capturado y liberado no existen en la intención y se devuelven como {@code null} [D-UC06-04].</p>
 *
 * <p>Fuente: UC06 plan T005, reglas 2–7; contrato {@code rest/UC06-confirmacion-pago.md} §§3–5.</p>
 */
public class GetPaymentConfirmationService implements GetPaymentConfirmationUseCase {

    private final ChargeIntentRepository chargeIntentRepository;

    public GetPaymentConfirmationService(ChargeIntentRepository chargeIntentRepository) {
        this.chargeIntentRepository = Objects.requireNonNull(chargeIntentRepository, "chargeIntentRepository");
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentConfirmationResult get(PaymentConfirmationQuery query) {
        Objects.requireNonNull(query, "query");
        ReservationId reservationId = Objects.requireNonNull(query.reservationId(), "query.reservationId");

        ChargeIntent intent = chargeIntentRepository.findLatestByReservationId(reservationId)
                .orElseThrow(() -> new ChargeIntentNotFoundException(
                        "There is no ChargeIntent for reservation " + reservationId.value() + "."));

        PaymentConfirmationStatus status = PaymentConfirmationStatus.from(intent.status());
        BigDecimal chargedAmount = status == PaymentConfirmationStatus.APPROVED
                ? intent.amount().toOutputAmount()
                : null;

        return new PaymentConfirmationResult(reservationId, status, intent.statusDetail(), null, null, null,
                chargedAmount, intent.externalReference());
    }
}
