package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.DisputeNotificationCommand;
import com.seashare.seasharem3.application.dto.RefundRequestCommand;
import com.seashare.seasharem3.application.dto.SettlementCommand;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.HandleDisputeNotificationUseCase;
import com.seashare.seasharem3.application.port.in.RequestRefundUseCase;
import com.seashare.seasharem3.application.port.in.RequestSettlementUseCase;
import com.seashare.seasharem3.application.port.out.DepositDispositionRepository;
import com.seashare.seasharem3.application.port.out.DisputeEventLogRepository;
import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.application.port.out.ReservationLockPort;
import com.seashare.seasharem3.domain.model.DisputeEvent;
import com.seashare.seasharem3.domain.valueobject.DepositDispositionStatus;
import com.seashare.seasharem3.domain.valueobject.DisputeStatus;
import com.seashare.seasharem3.domain.valueobject.RefundTrigger;
import com.seashare.seasharem3.domain.valueobject.SettlementScope;
import com.seashare.seasharem3.domain.valueobject.SettlementTrigger;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Procesa las notificaciones de disputa de UC08.
 *
 * <p>El evento se reclama insertandolo en {@code dispute_event_log}; la clave
 * {@code (reservation_id, dispute_id, event_key)} evita repetir la operacion. {@code PENDING} solo se
 * registra. Los estados finales se delegan en UC09/UC10, que recuperan los registros internos y
 * aplican el unico compare-and-set de {@code deposit_disposition}; UC08 comprueba antes su propia
 * disposicion y no repite un desenlace ya aplicado. Todo ocurre en una transaccion: si UC09/UC10
 * fallan de forma transitoria, el evento y el {@code processed_at} se revierten para reintentar.</p>
 *
 * <p>Fuente: UC08 plan T008/T009 y D-UC08-06.</p>
 */
public class DisputeInformationService implements HandleDisputeNotificationUseCase {

    private static final String USE_CASE = "UC08";

    private final DisputeEventLogRepository eventLog;
    private final DepositDispositionRepository depositDisposition;
    private final ReservationLockPort reservationLock;
    private final FailureRecorderPort failureRecorder;
    private final RequestRefundUseCase requestRefund;
    private final RequestSettlementUseCase requestSettlement;
    private final Clock clock;

    public DisputeInformationService(DisputeEventLogRepository eventLog,
            DepositDispositionRepository depositDisposition, ReservationLockPort reservationLock,
            FailureRecorderPort failureRecorder, RequestRefundUseCase requestRefund,
            RequestSettlementUseCase requestSettlement, Clock clock) {
        this.eventLog = Objects.requireNonNull(eventLog, "eventLog");
        this.depositDisposition = Objects.requireNonNull(depositDisposition, "depositDisposition");
        this.reservationLock = Objects.requireNonNull(reservationLock, "reservationLock");
        this.failureRecorder = Objects.requireNonNull(failureRecorder, "failureRecorder");
        this.requestRefund = Objects.requireNonNull(requestRefund, "requestRefund");
        this.requestSettlement = Objects.requireNonNull(requestSettlement, "requestSettlement");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public void handle(DisputeNotificationCommand command) {
        Objects.requireNonNull(command, "command");

        DisputeEvent event = new DisputeEvent(UUID.randomUUID(), command.reservationId(), command.disputeId(),
                command.eventKey(), command.status(), command.statusChangedAt(), null);
        if (!eventLog.insertIfAbsent(event)) {
            handleDuplicate(event);
            return;
        }
        process(event);
    }

    private void handleDuplicate(DisputeEvent event) {
        DisputeEvent stored = eventLog.findByKey(event.reservationId(), event.disputeId(), event.eventKey())
                .orElseThrow(() -> new IllegalStateException(
                        "dispute_event_log insert was rejected but the event does not exist: " + event.eventKey()));

        if (!stored.samePayload(event)) {
            recordConflict(event, "event_key_reused_with_different_payload");
            return;
        }
        if (stored.processedAt() != null) {
            return;
        }
        process(stored);
    }

    private void process(DisputeEvent event) {
        if (event.status() == DisputeStatus.PENDING) {
            markProcessed(event);
            return;
        }
        processFinal(event);
    }

    private void processFinal(DisputeEvent event) {
        DepositDispositionStatus target = targetFor(event.status());
        reservationLock.lock(event.reservationId());

        var current = depositDisposition.findByReservationId(event.reservationId());
        if (current.isPresent()) {
            DepositDispositionStatus disposition = current.get().disposition();
            if (disposition == target) {
                markProcessed(event);
                return;
            }
            if (disposition != DepositDispositionStatus.HELD) {
                recordConflict(event, "deposit_disposition=" + disposition + ";requested=" + target);
                markProcessed(event);
                return;
            }
        }

        delegate(event);
        markProcessed(event);
    }

    private void delegate(DisputeEvent event) {
        if (event.status() == DisputeStatus.REJECTED) {
            requestRefund.request(new RefundRequestCommand(event.reservationId().value(),
                    RefundTrigger.DISPUTE_REJECTED, event.disputeId().value(), event.eventKey()));
            return;
        }
        requestSettlement.request(new SettlementCommand(event.reservationId(),
                SettlementTrigger.DISPUTE_COMPLETED, SettlementScope.DEPOSIT, event.disputeId().value(),
                event.eventKey()));
    }

    private static DepositDispositionStatus targetFor(DisputeStatus status) {
        return switch (status) {
            case REJECTED -> DepositDispositionStatus.REFUND_REQUESTED;
            case COMPLETED -> DepositDispositionStatus.SETTLEMENT_REQUESTED;
            case PENDING -> throw new IllegalArgumentException("PENDING has no deposit disposition");
        };
    }

    private void markProcessed(DisputeEvent event) {
        if (!eventLog.markProcessed(event.id(), clock.instant())) {
            throw new IllegalStateException("Could not mark dispute event as processed: " + event.id());
        }
    }

    private void recordConflict(DisputeEvent event, String detail) {
        failureRecorder.record(USE_CASE, event.reservationId().value(), ErrorCode.INTERNAL_ERROR.name(),
                detail + ";eventKey=" + event.eventKey());
    }
}
