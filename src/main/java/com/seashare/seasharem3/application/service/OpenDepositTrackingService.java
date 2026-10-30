package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.DepositTrackingResult;
import com.seashare.seasharem3.application.dto.OpenDepositTrackingCommand;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.OpenDepositTrackingUseCase;
import com.seashare.seasharem3.application.port.out.DepositDispositionRepository;
import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.application.port.out.ReservationLockPort;
import com.seashare.seasharem3.domain.model.DepositDisposition;
import com.seashare.seasharem3.domain.valueobject.DepositDispositionStatus;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.time.Clock;
import java.util.Objects;

import org.springframework.transaction.annotation.Transactional;

/**
 * Implementacion minima de UC08 para el desenlace del deposito. UC07 abre la fila en HELD y
 * UC09/UC10 solicitan REFUND_REQUESTED/SETTLEMENT_REQUESTED; el repositorio aplica el unico
 * compare-and-set (general-plan D-34).
 *
 * <p>Si el deposito ya tiene el desenlace solicitado, la operacion es idempotente. Si ya tiene
 * otro desenlace, se registra la inconsistencia y se conserva el existente: el deposito no puede
 * reembolsarse y liquidarse a la vez.</p>
 */
public class OpenDepositTrackingService implements OpenDepositTrackingUseCase {

    private static final String USE_CASE = "UC08";

    private final DepositDispositionRepository repository;
    private final ReservationLockPort reservationLock;
    private final FailureRecorderPort failureRecorder;
    private final Clock clock;

    public OpenDepositTrackingService(DepositDispositionRepository repository, ReservationLockPort reservationLock,
            FailureRecorderPort failureRecorder, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.reservationLock = Objects.requireNonNull(reservationLock, "reservationLock");
        this.failureRecorder = Objects.requireNonNull(failureRecorder, "failureRecorder");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public DepositTrackingResult open(OpenDepositTrackingCommand command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.reservationId(), "command.reservationId");
        Objects.requireNonNull(command.target(), "command.target");
        reservationLock.lock(command.reservationId());

        return repository.findByReservationId(command.reservationId())
                .map(existing -> applyToExisting(command, existing))
                .orElseGet(() -> create(command));
    }

    private DepositTrackingResult applyToExisting(OpenDepositTrackingCommand command, DepositDisposition existing) {
        DepositDispositionStatus target = command.target();
        if (existing.disposition() == target) {
            return result(existing.reservationId(), existing.disposition(), false);
        }
        if (target == DepositDispositionStatus.HELD
                || existing.disposition() != DepositDispositionStatus.HELD) {
            recordConflict(command, existing);
            return result(existing.reservationId(), existing.disposition(), false);
        }

        DepositDisposition next = DepositDisposition.create(existing.reservationId(),
                command.completedAt() == null ? existing.completedAt() : command.completedAt(),
                command.disputeId() == null ? existing.disputeId() : command.disputeId(),
                command.disputeStatus() == null ? existing.disputeStatus() : command.disputeStatus(), target);
        boolean changed = repository.compareAndSet(existing.reservationId(), existing.disposition(), next);
        if (!changed) {
            DepositDisposition current = repository.findByReservationId(command.reservationId()).orElse(existing);
            if (current.disposition() == target) {
                return result(current.reservationId(), current.disposition(), false);
            }
            recordConflict(command, current);
            return result(current.reservationId(), current.disposition(), false);
        }
        return result(next.reservationId(), next.disposition(), false);
    }

    private DepositTrackingResult create(OpenDepositTrackingCommand command) {
        var completedAt = command.completedAt() == null ? clock.instant() : command.completedAt();
        DepositDisposition held = DepositDisposition.create(command.reservationId(), completedAt,
                command.disputeId(), command.disputeStatus(), DepositDispositionStatus.HELD);
        repository.save(held);
        if (command.target() == DepositDispositionStatus.HELD) {
            return result(held.reservationId(), held.disposition(), true);
        }
        DepositDisposition next = DepositDisposition.create(command.reservationId(), completedAt,
                command.disputeId(), command.disputeStatus(), command.target());
        if (!repository.compareAndSet(command.reservationId(), DepositDispositionStatus.HELD, next)) {
            DepositDisposition current = repository.findByReservationId(command.reservationId()).orElse(held);
            recordConflict(command, current);
            return result(current.reservationId(), current.disposition(), true);
        }
        return result(next.reservationId(), next.disposition(), true);
    }

    private void recordConflict(OpenDepositTrackingCommand command, DepositDisposition existing) {
        failureRecorder.record(USE_CASE, command.reservationId().value(), ErrorCode.INTERNAL_ERROR.name(),
                "deposit_disposition=" + existing.disposition() + ";requested=" + command.target()
                        + ";operationKey=" + command.operationKey());
    }

    private static DepositTrackingResult result(ReservationId reservationId, DepositDispositionStatus status,
            boolean created) {
        return new DepositTrackingResult(reservationId, status, created);
    }
}
