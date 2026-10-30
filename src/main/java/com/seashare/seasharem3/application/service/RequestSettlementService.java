package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.OpenDepositTrackingCommand;
import com.seashare.seasharem3.application.dto.SettlementCommand;
import com.seashare.seasharem3.application.dto.SettlementRequestResult;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.OpenDepositTrackingUseCase;
import com.seashare.seasharem3.application.port.in.RequestSettlementUseCase;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.application.port.out.ChargeRecordQueryPort;
import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.application.port.out.FinancialParametersRepository;
import com.seashare.seasharem3.application.port.out.ReservationInformationRepository;
import com.seashare.seasharem3.application.port.out.SettlementCommandPublisher;
import com.seashare.seasharem3.application.port.out.SettlementIntentRepository;
import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.model.ChargeRecord;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.model.SettlementIntent;
import com.seashare.seasharem3.domain.service.SettlementCalculator;
import com.seashare.seasharem3.domain.valueobject.DepositDispositionStatus;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import com.seashare.seasharem3.domain.valueobject.RefundBreakdown;
import com.seashare.seasharem3.domain.valueobject.SettlementCalculation;
import com.seashare.seasharem3.domain.valueobject.SettlementScope;
import com.seashare.seasharem3.domain.valueobject.SettlementStatus;
import com.seashare.seasharem3.domain.valueobject.SettlementTrigger;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta UC10: recupera el desglose interno, calcula la liquidacion, conserva la comision en la
 * intencion y publica el comando en el outbox. No recibe montos desde Reservas.
 */
public class RequestSettlementService implements RequestSettlementUseCase {

    private static final String USE_CASE = "UC10";

    private final SettlementIntentRepository settlementIntentRepository;
    private final ChargeIntentRepository chargeIntentRepository;
    private final ChargeRecordQueryPort chargeRecordQueryPort;
    private final ReservationInformationRepository reservationInformationRepository;
    private final FinancialParametersRepository financialParametersRepository;
    private final SettlementCalculator settlementCalculator;
    private final SettlementCommandPublisher settlementCommandPublisher;
    private final OpenDepositTrackingUseCase openDepositTracking;
    private final FailureRecorderPort failureRecorder;
    private final Clock clock;

    public RequestSettlementService(SettlementIntentRepository settlementIntentRepository,
            ChargeIntentRepository chargeIntentRepository, ChargeRecordQueryPort chargeRecordQueryPort,
            ReservationInformationRepository reservationInformationRepository,
            FinancialParametersRepository financialParametersRepository, SettlementCalculator settlementCalculator,
            SettlementCommandPublisher settlementCommandPublisher, OpenDepositTrackingUseCase openDepositTracking,
            FailureRecorderPort failureRecorder, Clock clock) {
        this.settlementIntentRepository = Objects.requireNonNull(settlementIntentRepository,
                "settlementIntentRepository");
        this.chargeIntentRepository = Objects.requireNonNull(chargeIntentRepository, "chargeIntentRepository");
        this.chargeRecordQueryPort = Objects.requireNonNull(chargeRecordQueryPort, "chargeRecordQueryPort");
        this.reservationInformationRepository = Objects.requireNonNull(reservationInformationRepository,
                "reservationInformationRepository");
        this.financialParametersRepository = Objects.requireNonNull(financialParametersRepository,
                "financialParametersRepository");
        this.settlementCalculator = Objects.requireNonNull(settlementCalculator, "settlementCalculator");
        this.settlementCommandPublisher = Objects.requireNonNull(settlementCommandPublisher,
                "settlementCommandPublisher");
        this.openDepositTracking = Objects.requireNonNull(openDepositTracking, "openDepositTracking");
        this.failureRecorder = Objects.requireNonNull(failureRecorder, "failureRecorder");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public SettlementRequestResult request(SettlementCommand command) {
        Objects.requireNonNull(command, "command");
        IdempotencyKey key = new IdempotencyKey("settlement:" + command.trigger() + ":"
                + Objects.requireNonNull(command.operationKey(), "command.operationKey"));

        Optional<SettlementIntent> existing = settlementIntentRepository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            return new SettlementRequestResult(existing.get().id(), existing.get().status());
        }

        Optional<ChargeIntent> approvedCharge = chargeIntentRepository
                .findApprovedByReservationId(command.reservationId());
        if (approvedCharge.isEmpty()) {
            return failed(command, ErrorCode.CHARGE_INTENT_NOT_FOUND, "No captured charge");
        }
        ChargeIntent charge = approvedCharge.get();

        Optional<ReservationInformation> reservation = reservationInformationRepository
                .findByReservationId(command.reservationId()).filter(ReservationInformation::isCalculated);
        Optional<RefundBreakdown> breakdown = loadBreakdown(charge, reservation);
        if (breakdown.isEmpty()) {
            return failed(command, ErrorCode.RESERVATION_INFO_NOT_FOUND, "No internal breakdown");
        }

        Optional<Percentage> commissionPct = commissionPercentage(command, reservation);
        if (command.scope() == SettlementScope.STANDARD && commissionPct.isEmpty()) {
            return failed(command, ErrorCode.FINANCIAL_PARAMETERS_NOT_CONFIGURED,
                    "No frozen commission percentage");
        }

        SettlementCalculation calculation;
        try {
            calculation = settlementCalculator.calculate(command.trigger(), breakdown.get().rentalAmount(),
                    breakdown.get().insuranceAmount(), breakdown.get().depositAmount(), commissionPct.orElse(null));
        } catch (DomainException exception) {
            return failed(command, ErrorCode.RESERVATION_INFO_INCOMPLETE, exception.getMessage());
        }
        if (!calculation.disbursedAmount().isPositive()) {
            return failed(command, ErrorCode.RESERVATION_INFO_INCOMPLETE,
                    "The settlement amount must be greater than zero");
        }

        if (command.trigger() == SettlementTrigger.DISPUTE_COMPLETED && !requestDepositDisposition(command)) {
            return new SettlementRequestResult(null, SettlementStatus.FAILED);
        }

        SettlementIntent intent = SettlementIntent.create(UUID.randomUUID(), command.reservationId(),
                command.trigger(), command.scope(), calculation.disbursedAmount(),
                calculation.commissionAmount(), calculation.depositAmount(), charge.id(), key, command.disputeId(),
                clock.instant());
        settlementIntentRepository.save(intent);
        settlementCommandPublisher.publish(intent.id());
        return new SettlementRequestResult(intent.id(), intent.status());
    }

    private Optional<Percentage> commissionPercentage(SettlementCommand command,
            Optional<ReservationInformation> reservation) {
        if (command.scope() != SettlementScope.STANDARD) {
            return Optional.of(Percentage.ZERO);
        }
        if (reservation.isEmpty() || reservation.get().frozenParametersId() == null) {
            return Optional.empty();
        }
        return financialParametersRepository.findById(reservation.get().frozenParametersId())
                .map(FinancialParameters::commissionPct);
    }

    private boolean requestDepositDisposition(SettlementCommand command) {
        var result = openDepositTracking.open(new OpenDepositTrackingCommand(command.reservationId(),
                command.operationKey(), DepositDispositionStatus.SETTLEMENT_REQUESTED, command.disputeId(),
                null, null));
        if (result.status() == DepositDispositionStatus.SETTLEMENT_REQUESTED) {
            return true;
        }
        failureRecorder.record(USE_CASE, command.reservationId().value(), ErrorCode.INTERNAL_ERROR.name(),
                "deposit_disposition=" + result.status() + ";operationKey=" + command.operationKey());
        return false;
    }

    private Optional<RefundBreakdown> loadBreakdown(ChargeIntent charge,
            Optional<ReservationInformation> reservation) {
        Optional<ChargeRecord> record = chargeRecordQueryPort.findByChargeIntentId(charge.id());
        if (record.isPresent()) {
            ChargeRecord value = record.get();
            return Optional.of(new RefundBreakdown(value.rentalAmount(), value.insuranceAmount(), value.depositAmount(), value.amount()));
        }
        return reservation.map(info -> new RefundBreakdown(info.rentalAmount(), info.insuranceAmount(),
                info.depositAmount(), info.totalAmount()));
    }

    private SettlementRequestResult failed(SettlementCommand command, ErrorCode code, String detail) {
        failureRecorder.record(USE_CASE, command.reservationId().value(), code.name(),
                "trigger=" + command.trigger() + ";scope=" + command.scope() + ";reason=" + detail
                        + ";operationKey=" + command.operationKey());
        return new SettlementRequestResult(null, SettlementStatus.FAILED);
    }

}
