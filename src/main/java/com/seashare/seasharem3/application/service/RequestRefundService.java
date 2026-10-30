package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.OpenDepositTrackingCommand;
import com.seashare.seasharem3.application.dto.RefundRequestCommand;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.OpenDepositTrackingUseCase;
import com.seashare.seasharem3.application.port.in.RequestRefundUseCase;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.application.port.out.ChargeRecordQueryPort;
import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.application.port.out.RefundCommandPublisher;
import com.seashare.seasharem3.application.port.out.RefundIntentRepository;
import com.seashare.seasharem3.application.port.out.ReservationInformationRepository;
import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.model.ChargeRecord;
import com.seashare.seasharem3.domain.model.RefundIntent;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.service.RefundCalculator;
import com.seashare.seasharem3.domain.valueobject.DepositDispositionStatus;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.RefundBreakdown;
import com.seashare.seasharem3.domain.valueobject.RefundCalculation;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta UC09/HU1 y HU2: valida la solicitud, recupera el cobro capturado y el desglose
 * inmutable, calcula el monto, registra la intencion y publica el comando en el outbox.
 *
 * <p>La fuente autoritativa del desglose es {@code charge_record}; si el cobro no lo tiene se
 * usa la informacion de reserva calculada como respaldo (D-UC09-08). La intencion y el mensaje se
 * confirman en la misma transaccion (D-08) y el desenlace del deposito se aplica antes de solicitar
 * el reembolso, para no reembolsar un deposito ya liquidado (D-34).</p>
 */
public class RequestRefundService implements RequestRefundUseCase {

    private static final String USE_CASE = "UC09";

    private final RefundIntentRepository refundIntentRepository;
    private final ChargeIntentRepository chargeIntentRepository;
    private final ChargeRecordQueryPort chargeRecordQueryPort;
    private final ReservationInformationRepository reservationInformationRepository;
    private final RefundCalculator refundCalculator;
    private final RefundCommandPublisher refundCommandPublisher;
    private final OpenDepositTrackingUseCase openDepositTracking;
    private final FailureRecorderPort failureRecorder;
    private final Clock clock;

    public RequestRefundService(RefundIntentRepository refundIntentRepository,
            ChargeIntentRepository chargeIntentRepository,
            ChargeRecordQueryPort chargeRecordQueryPort,
            ReservationInformationRepository reservationInformationRepository,
            RefundCalculator refundCalculator,
            RefundCommandPublisher refundCommandPublisher,
            OpenDepositTrackingUseCase openDepositTracking,
            FailureRecorderPort failureRecorder,
            Clock clock) {
        this.refundIntentRepository = Objects.requireNonNull(refundIntentRepository, "refundIntentRepository");
        this.chargeIntentRepository = Objects.requireNonNull(chargeIntentRepository, "chargeIntentRepository");
        this.chargeRecordQueryPort = Objects.requireNonNull(chargeRecordQueryPort, "chargeRecordQueryPort");
        this.reservationInformationRepository = Objects.requireNonNull(reservationInformationRepository,
                "reservationInformationRepository");
        this.refundCalculator = Objects.requireNonNull(refundCalculator, "refundCalculator");
        this.refundCommandPublisher = Objects.requireNonNull(refundCommandPublisher, "refundCommandPublisher");
        this.openDepositTracking = Objects.requireNonNull(openDepositTracking, "openDepositTracking");
        this.failureRecorder = Objects.requireNonNull(failureRecorder, "failureRecorder");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public void request(RefundRequestCommand command) {
        Objects.requireNonNull(command, "command");
        ReservationId reservationId = new ReservationId(
                Objects.requireNonNull(command.reservationId(), "command.reservationId"));
        IdempotencyKey key = new IdempotencyKey("refund:" + command.trigger() + ":"
                + Objects.requireNonNull(command.operationKey(), "command.operationKey"));

        if (refundIntentRepository.findByIdempotencyKey(key).isPresent()) {
            return;
        }

        Optional<ChargeIntent> approvedCharge = chargeIntentRepository.findApprovedByReservationId(reservationId);
        if (approvedCharge.isEmpty()) {
            failureRecorder.record(USE_CASE, reservationId.value(), ErrorCode.CHARGE_INTENT_NOT_FOUND.name(),
                    "reservationId=" + reservationId.value());
            return;
        }
        ChargeIntent charge = approvedCharge.get();

        Optional<RefundBreakdown> breakdown = loadBreakdown(charge);
        if (breakdown.isEmpty()) {
            failureRecorder.record(USE_CASE, reservationId.value(), ErrorCode.RESERVATION_INFO_NOT_FOUND.name(),
                    "reservationId=" + reservationId.value() + ";chargeIntentId=" + charge.id());
            return;
        }

        RefundCalculation calculation;
        try {
            calculation = refundCalculator.calculate(command.trigger(), breakdown.get().rentalAmount(),
                    breakdown.get().insuranceAmount(), breakdown.get().depositAmount(),
                    breakdown.get().totalAmount());
        } catch (DomainException exception) {
            failureRecorder.record(USE_CASE, reservationId.value(), ErrorCode.RESERVATION_INFO_INCOMPLETE.name(),
                    "reservationId=" + reservationId.value() + ";reason=" + exception.getMessage());
            return;
        }
        if (!calculation.amount().isPositive()) {
            failureRecorder.record(USE_CASE, reservationId.value(), ErrorCode.RESERVATION_INFO_INCOMPLETE.name(),
                    "reservationId=" + reservationId.value() + ";reason=refund amount must be positive");
            return;
        }

        if (!requestDepositDisposition(command, reservationId)) {
            return;
        }

        RefundIntent intent = RefundIntent.create(UUID.randomUUID(), reservationId, command.trigger(),
                calculation.scope(), calculation.amount(), charge.id(), key, command.disputeId(),
                clock.instant());
        refundIntentRepository.save(intent);
        refundCommandPublisher.publish(intent.id());
    }

    /** Aplica el desenlace del deposito; si ya fue liquidado, se registra y no se reembolsa. */
    private boolean requestDepositDisposition(RefundRequestCommand command, ReservationId reservationId) {
        var result = openDepositTracking.open(new OpenDepositTrackingCommand(reservationId, command.operationKey(),
                DepositDispositionStatus.REFUND_REQUESTED, command.disputeId(), null, null));
        if (result.status() == DepositDispositionStatus.REFUND_REQUESTED) {
            return true;
        }
        failureRecorder.record(USE_CASE, reservationId.value(), ErrorCode.INTERNAL_ERROR.name(),
                "deposit_disposition=" + result.status() + ";operationKey=" + command.operationKey());
        return false;
    }

    private Optional<RefundBreakdown> loadBreakdown(ChargeIntent charge) {
        Optional<ChargeRecord> record = chargeRecordQueryPort.findByChargeIntentId(charge.id());
        if (record.isPresent()) {
            ChargeRecord value = record.get();
            return Optional.of(new RefundBreakdown(value.rentalAmount(), value.insuranceAmount(), value.depositAmount(),
                    value.amount()));
        }
        return reservationInformationRepository.findByReservationId(charge.reservationId())
                .filter(ReservationInformation::isCalculated)
                .map(info -> new RefundBreakdown(info.rentalAmount(), info.insuranceAmount(), info.depositAmount(),
                        info.totalAmount()));
    }

}
