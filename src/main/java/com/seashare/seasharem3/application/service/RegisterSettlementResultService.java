package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.GatewaySettlementResult;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.RegisterSettlementResultUseCase;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.application.port.out.ChargeRecordQueryPort;
import com.seashare.seasharem3.application.port.out.CommissionRecordRepository;
import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.application.port.out.ReservationInformationRepository;
import com.seashare.seasharem3.application.port.out.SettlementIntentRepository;
import com.seashare.seasharem3.application.port.out.SettlementRecordRepository;
import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.model.ChargeRecord;
import com.seashare.seasharem3.domain.model.CommissionRecord;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.model.SettlementIntent;
import com.seashare.seasharem3.domain.model.SettlementRecord;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.SettlementBreakdown;
import com.seashare.seasharem3.domain.valueobject.SettlementScope;
import com.seashare.seasharem3.domain.valueobject.SettlementStatus;

import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

/**
 * Registra el resultado de la dispersion. La intencion se actualiza siempre; el registro y la
 * comision (solo estandar) se crean atomicamente en COMPLETED y una sola vez (RF-010/RF-014).
 */
public class RegisterSettlementResultService implements RegisterSettlementResultUseCase {

    private static final String USE_CASE = "UC10";

    private final SettlementIntentRepository settlementIntentRepository;
    private final SettlementRecordRepository settlementRecordRepository;
    private final CommissionRecordRepository commissionRecordRepository;
    private final ChargeIntentRepository chargeIntentRepository;
    private final ChargeRecordQueryPort chargeRecordQueryPort;
    private final ReservationInformationRepository reservationInformationRepository;
    private final FailureRecorderPort failureRecorder;
    private final Clock clock;

    public RegisterSettlementResultService(SettlementIntentRepository settlementIntentRepository,
            SettlementRecordRepository settlementRecordRepository,
            CommissionRecordRepository commissionRecordRepository, ChargeIntentRepository chargeIntentRepository,
            ChargeRecordQueryPort chargeRecordQueryPort,
            ReservationInformationRepository reservationInformationRepository, FailureRecorderPort failureRecorder,
            Clock clock) {
        this.settlementIntentRepository = Objects.requireNonNull(settlementIntentRepository,
                "settlementIntentRepository");
        this.settlementRecordRepository = Objects.requireNonNull(settlementRecordRepository,
                "settlementRecordRepository");
        this.commissionRecordRepository = Objects.requireNonNull(commissionRecordRepository,
                "commissionRecordRepository");
        this.chargeIntentRepository = Objects.requireNonNull(chargeIntentRepository, "chargeIntentRepository");
        this.chargeRecordQueryPort = Objects.requireNonNull(chargeRecordQueryPort, "chargeRecordQueryPort");
        this.reservationInformationRepository = Objects.requireNonNull(reservationInformationRepository,
                "reservationInformationRepository");
        this.failureRecorder = Objects.requireNonNull(failureRecorder, "failureRecorder");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public void register(GatewaySettlementResult result) {
        Objects.requireNonNull(result, "result");
        SettlementIntent intent = findIntent(result.externalId());
        if (intent == null) {
            failureRecorder.record(USE_CASE, null, ErrorCode.SETTLEMENT_INTENT_NOT_FOUND.name(), payloadRef(result));
            return;
        }

        SettlementStatus status = toStatus(result.status());
        if (status == null || !intent.canTransitionTo(status)) {
            failureRecorder.record(USE_CASE, intent.reservationId().value(), ErrorCode.INTERNAL_ERROR.name(),
                    payloadRef(result));
            return;
        }

        SettlementIntent updated = intent.transitionTo(status);
        if (result.statusDetail() != null) {
            updated = updated.withStatusDetail(result.statusDetail());
        }
        settlementIntentRepository.save(updated);
        if (status == SettlementStatus.COMPLETED) {
            createRecords(updated, result);
        }
    }

    private SettlementIntent findIntent(String externalReference) {
        if (externalReference == null || externalReference.isBlank()) {
            return null;
        }
        return settlementIntentRepository.findByExternalReference(externalReference).orElse(null);
    }

    private void createRecords(SettlementIntent intent, GatewaySettlementResult result) {
        Optional<SettlementRecord> existing = settlementRecordRepository.findBySettlementIntentId(intent.id());
        if (existing.isPresent()) {
            createCommissionIfNeeded(intent, existing.get());
            return;
        }

        Optional<SettlementBreakdown> breakdown = resolveBreakdown(intent);
        if (breakdown.isEmpty()) {
            failureRecorder.record(USE_CASE, intent.reservationId().value(),
                    ErrorCode.RESERVATION_INFO_NOT_FOUND.name(), "settlementIntentId=" + intent.id());
            return;
        }
        Money confirmed = result.amount() == null ? intent.amount() : result.amount();
        Money insurance = intent.scope() == SettlementScope.STANDARD ? breakdown.get().insuranceAmount() : Money.ZERO;
        try {
            SettlementRecord record = settlementRecordRepository.save(SettlementRecord.create(UUID.randomUUID(),
                    intent, breakdown.get().ownerId(), breakdown.get().boatId(), confirmed, result.statusDetail(),
                    breakdown.get().rentalAmount(), insurance, intent.depositAmount(), result.externalId(),
                    clock.instant()));
            createCommissionIfNeeded(intent, record);
        } catch (DomainException exception) {
            failureRecorder.record(USE_CASE, intent.reservationId().value(), ErrorCode.INTERNAL_ERROR.name(),
                    "settlementIntentId=" + intent.id() + ";reason=" + exception.getMessage());
        }
    }

    private void createCommissionIfNeeded(SettlementIntent intent, SettlementRecord record) {
        if (intent.scope() != SettlementScope.STANDARD
                || commissionRecordRepository.findBySettlementRecordId(record.id()).isPresent()) {
            return;
        }
        commissionRecordRepository.save(CommissionRecord.create(UUID.randomUUID(), record,
                intent.commissionAmount(), clock.instant()));
    }

    private Optional<SettlementBreakdown> resolveBreakdown(SettlementIntent intent) {
        Optional<com.seashare.seasharem3.domain.model.ChargeIntent> charge = chargeIntentRepository
                .findById(intent.chargeIntentId());
        if (charge.isPresent()) {
            Optional<ChargeRecord> record = chargeRecordQueryPort.findByChargeIntentId(charge.get().id());
            if (record.isPresent()) {
                return Optional.of(new SettlementBreakdown(record.get().ownerId(), record.get().boatId(),
                        record.get().rentalAmount(), record.get().insuranceAmount()));
            }
        }
        return reservationInformationRepository.findByReservationId(intent.reservationId())
                .map(info -> new SettlementBreakdown(info.ownerId(), info.boatId(), info.rentalAmount(),
                        info.insuranceAmount()));
    }

    private static SettlementStatus toStatus(String externalStatus) {
        if (externalStatus == null) {
            return null;
        }
        return switch (externalStatus.toLowerCase(Locale.ROOT)) {
            case "approved" -> SettlementStatus.COMPLETED;
            case "in_process", "pending" -> SettlementStatus.IN_PROCESS;
            case "rejected" -> SettlementStatus.REJECTED;
            case "cancelled" -> SettlementStatus.CANCELLED;
            case "expired" -> SettlementStatus.EXPIRED;
            default -> null;
        };
    }

    private static String payloadRef(GatewaySettlementResult result) {
        return "externalReference=" + result.externalId() + ";status=" + result.status();
    }

}
