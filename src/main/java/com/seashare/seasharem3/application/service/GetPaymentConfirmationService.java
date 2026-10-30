package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.PaymentConfirmationQuery;
import com.seashare.seasharem3.application.dto.PaymentConfirmationResult;
import com.seashare.seasharem3.application.port.in.GetPaymentConfirmationUseCase;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.application.port.out.RefundRecordQueryPort;
import com.seashare.seasharem3.domain.exception.ChargeIntentNotFoundException;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.valueobject.PaymentConfirmationStatus;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Consulta de solo lectura del estado vigente del cobro de una reserva. Lee la intencion mas
 * reciente de UC05 y agrega el monto neto liberado confirmado por UC09; no contacta la pasarela.
 * Fuente: UC06 plan T005 y D-UC06-04.
 */
public class GetPaymentConfirmationService implements GetPaymentConfirmationUseCase {

    private final ChargeIntentRepository chargeIntentRepository;
    private final RefundRecordQueryPort refundRecordQueryPort;

    public GetPaymentConfirmationService(ChargeIntentRepository chargeIntentRepository,
            RefundRecordQueryPort refundRecordQueryPort) {
        this.chargeIntentRepository = Objects.requireNonNull(chargeIntentRepository, "chargeIntentRepository");
        this.refundRecordQueryPort = Objects.requireNonNull(refundRecordQueryPort, "refundRecordQueryPort");
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
        BigDecimal releasedAmount = refundRecordQueryPort.releasedAmountByReservationId(reservationId)
                .map(money -> money.toOutputAmount())
                .orElse(null);

        return new PaymentConfirmationResult(reservationId, status, intent.statusDetail(), null, null,
                releasedAmount, chargedAmount, intent.externalReference());
    }
}
