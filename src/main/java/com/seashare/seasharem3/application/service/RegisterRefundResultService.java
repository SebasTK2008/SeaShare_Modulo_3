package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.RefundGatewayResult;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.RegisterRefundResultUseCase;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.application.port.out.ChargeRecordQueryPort;
import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.application.port.out.RefundIntentRepository;
import com.seashare.seasharem3.application.port.out.RefundRecordRepository;
import com.seashare.seasharem3.application.port.out.ReservationInformationRepository;
import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.exception.DuplicateRecordException;
import com.seashare.seasharem3.domain.model.ChargeRecord;
import com.seashare.seasharem3.domain.model.RefundIntent;
import com.seashare.seasharem3.domain.model.RefundRecord;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerBoat;
import com.seashare.seasharem3.domain.valueobject.RefundIntentStatus;

import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

/**
 * Registra el resultado del reembolso. La intencion se actualiza siempre; el registro inmutable
 * solo se crea en COMPLETED y una sola vez por intencion (RF-007, RF-009, D-UC09-06).
 */
public class RegisterRefundResultService implements RegisterRefundResultUseCase {

    private static final String USE_CASE = "UC09";

    private final RefundIntentRepository refundIntentRepository;
    private final RefundRecordRepository refundRecordRepository;
    private final ChargeIntentRepository chargeIntentRepository;
    private final ChargeRecordQueryPort chargeRecordQueryPort;
    private final ReservationInformationRepository reservationInformationRepository;
    private final FailureRecorderPort failureRecorder;
    private final Clock clock;

    public RegisterRefundResultService(RefundIntentRepository refundIntentRepository,
            RefundRecordRepository refundRecordRepository, ChargeIntentRepository chargeIntentRepository,
            ChargeRecordQueryPort chargeRecordQueryPort,
            ReservationInformationRepository reservationInformationRepository, FailureRecorderPort failureRecorder,
            Clock clock) {
        this.refundIntentRepository = Objects.requireNonNull(refundIntentRepository, "refundIntentRepository");
        this.refundRecordRepository = Objects.requireNonNull(refundRecordRepository, "refundRecordRepository");
        this.chargeIntentRepository = Objects.requireNonNull(chargeIntentRepository, "chargeIntentRepository");
        this.chargeRecordQueryPort = Objects.requireNonNull(chargeRecordQueryPort, "chargeRecordQueryPort");
        this.reservationInformationRepository = Objects.requireNonNull(reservationInformationRepository,
                "reservationInformationRepository");
        this.failureRecorder = Objects.requireNonNull(failureRecorder, "failureRecorder");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public void register(RefundGatewayResult result) {
        Objects.requireNonNull(result, "result");
        RefundIntent intent = findIntent(result.externalId());
        if (intent == null) {
            failureRecorder.record(USE_CASE, null, ErrorCode.REFUND_INTENT_NOT_FOUND.name(), payloadRef(result));
            return;
        }

        RefundIntentStatus status = toStatus(result.status());
        if (status == null || !intent.canTransitionTo(status)) {
            failureRecorder.record(USE_CASE, intent.reservationId().value(), ErrorCode.INTERNAL_ERROR.name(),
                    payloadRef(result));
            return;
        }

        RefundIntent updated = intent.transitionTo(status);
        if (result.statusDetail() != null) {
            updated = updated.withStatusDetail(result.statusDetail());
        }
        refundIntentRepository.save(updated);
        if (status == RefundIntentStatus.COMPLETED) {
            createRecord(updated, result);
        }
    }

    private RefundIntent findIntent(String externalReference) {
        if (externalReference == null || externalReference.isBlank()) {
            return null;
        }
        return refundIntentRepository.findByExternalReference(externalReference).orElse(null);
    }

    private void createRecord(RefundIntent intent, RefundGatewayResult result) {
        if (refundRecordRepository.findByRefundIntentId(intent.id()).isPresent()) {
            return;
        }
        Optional<OwnerBoat> ownerBoat = resolveOwnerBoat(intent);
        if (ownerBoat.isEmpty()) {
            failureRecorder.record(USE_CASE, intent.reservationId().value(),
                    ErrorCode.RESERVATION_INFO_NOT_FOUND.name(), "refundIntentId=" + intent.id());
            return;
        }
        Money confirmed = result.amount() == null ? intent.amount() : result.amount();
        try {
            refundRecordRepository.save(RefundRecord.create(UUID.randomUUID(), intent,
                    ownerBoat.get().ownerId(), ownerBoat.get().boatId(), confirmed, result.transactionCost(),
                    result.statusDetail(), result.externalId(), clock.instant()));
        } catch (DuplicateRecordException exception) {
            return;
        } catch (DomainException exception) {
            failureRecorder.record(USE_CASE, intent.reservationId().value(), ErrorCode.INTERNAL_ERROR.name(),
                    "refundIntentId=" + intent.id() + ";reason=" + exception.getMessage());
        }
    }

    private Optional<OwnerBoat> resolveOwnerBoat(RefundIntent intent) {
        Optional<com.seashare.seasharem3.domain.model.ChargeIntent> charge = chargeIntentRepository
                .findById(intent.chargeIntentId());
        if (charge.isPresent()) {
            Optional<ChargeRecord> record = chargeRecordQueryPort.findByChargeIntentId(charge.get().id());
            if (record.isPresent()) {
                return Optional.of(new OwnerBoat(record.get().ownerId(), record.get().boatId()));
            }
        }
        return reservationInformationRepository.findByReservationId(intent.reservationId())
                .map(info -> new OwnerBoat(info.ownerId(), info.boatId()));
    }

    private static RefundIntentStatus toStatus(String externalStatus) {
        if (externalStatus == null) {
            return null;
        }
        return switch (externalStatus.toLowerCase(Locale.ROOT)) {
            case "approved" -> RefundIntentStatus.COMPLETED;
            case "in_process", "pending" -> RefundIntentStatus.IN_PROCESS;
            case "rejected" -> RefundIntentStatus.REJECTED;
            case "cancelled" -> RefundIntentStatus.CANCELLED;
            case "expired" -> RefundIntentStatus.EXPIRED;
            default -> null;
        };
    }

    private static String payloadRef(RefundGatewayResult result) {
        return "externalReference=" + result.externalId() + ";status=" + result.status();
    }
}
