package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.DepositTrackingResult;
import com.seashare.seasharem3.application.dto.OpenDepositTrackingCommand;
import com.seashare.seasharem3.application.dto.ProcessChargeCommand;
import com.seashare.seasharem3.application.dto.RefundRequestCommand;
import com.seashare.seasharem3.application.dto.ReservationStatusCommand;
import com.seashare.seasharem3.application.dto.SettlementCommand;
import com.seashare.seasharem3.application.dto.SettlementRequestResult;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.HandleReservationStatusUseCase;
import com.seashare.seasharem3.application.port.in.OpenDepositTrackingUseCase;
import com.seashare.seasharem3.application.port.in.ProcessChargeUseCase;
import com.seashare.seasharem3.application.port.in.RequestRefundUseCase;
import com.seashare.seasharem3.application.port.in.RequestSettlementUseCase;
import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.application.port.out.ReservationInformationRepository;
import com.seashare.seasharem3.application.port.out.ReservationLockPort;
import com.seashare.seasharem3.application.port.out.ReservationStatusLogRepository;
import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.service.ReservationStatusPolicy;
import com.seashare.seasharem3.domain.valueobject.DepositDispositionStatus;
import com.seashare.seasharem3.domain.valueobject.FinancialOperationType;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.PlannedOperation;
import com.seashare.seasharem3.domain.valueobject.RefundTrigger;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.domain.valueobject.ReservationStatus;
import com.seashare.seasharem3.domain.valueobject.SettlementScope;
import com.seashare.seasharem3.domain.valueobject.SettlementStatus;
import com.seashare.seasharem3.domain.valueobject.SettlementTrigger;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Orquesta UC07: reclama la identidad única de cada operación en {@code reservation_status_log} y
 * delega en UC05/UC09/UC10 (y UC08 para el seguimiento del depósito) dentro de una sola transacción.
 *
 * <p>La clave idempotente es por intento en {@code PENDING} —
 * {@code reserva:estado:statusChangedAt} — para que un rechazo de pago habilite un nuevo intento, y
 * estable en los estados terminales — {@code reserva:estado} — para que un reenvío con otra marca de
 * tiempo no repita la operación (D-UC07-11, general-plan D-33). La misma clave viaja como
 * {@code operationKey} a los casos de uso delegados.</p>
 *
 * <p>Los estados sin acción financiera se reconocen y se confirman sin tocar la base de datos. Si la
 * reserva no existe o no tiene montos calculados no se delega nada: se registra el fallo
 * (RF-010, CE-004). Una liquidación delegada que devuelve {@code FAILED} propaga una excepción de
 * dominio y la transacción revierte el mensaje completo (§Reglas 9). Una fila reclamada sin
 * {@code processed_at} indica una operación que no llegó a confirmarse y queda como señal para el
 * reproceso manual: un reenvío con la misma identidad no la repite (§Reglas 7-8).</p>
 *
 * <p>Fuente: UC07 plan §Reglas 1-10, T005/T006/T013/T014; contrato {@code events/UC07-estado-reserva.md}.</p>
 */
public class HandleReservationStatusService implements HandleReservationStatusUseCase {

    private static final String USE_CASE = "UC07";
    private static final String OUTCOME_DELEGATED = "delegated";

    private final ReservationInformationRepository reservationInformation;
    private final ReservationStatusLogRepository statusLog;
    private final ReservationLockPort reservationLock;
    private final FailureRecorderPort failureRecorder;
    private final ProcessChargeUseCase processCharge;
    private final RequestRefundUseCase requestRefund;
    private final RequestSettlementUseCase requestSettlement;
    private final OpenDepositTrackingUseCase openDepositTracking;
    private final ReservationStatusPolicy policy;
    private final Clock clock;

    public HandleReservationStatusService(ReservationInformationRepository reservationInformation,
            ReservationStatusLogRepository statusLog, ReservationLockPort reservationLock,
            FailureRecorderPort failureRecorder, ProcessChargeUseCase processCharge,
            RequestRefundUseCase requestRefund, RequestSettlementUseCase requestSettlement,
            OpenDepositTrackingUseCase openDepositTracking, ReservationStatusPolicy policy, Clock clock) {
        this.reservationInformation = Objects.requireNonNull(reservationInformation, "reservationInformation");
        this.statusLog = Objects.requireNonNull(statusLog, "statusLog");
        this.reservationLock = Objects.requireNonNull(reservationLock, "reservationLock");
        this.failureRecorder = Objects.requireNonNull(failureRecorder, "failureRecorder");
        this.processCharge = Objects.requireNonNull(processCharge, "processCharge");
        this.requestRefund = Objects.requireNonNull(requestRefund, "requestRefund");
        this.requestSettlement = Objects.requireNonNull(requestSettlement, "requestSettlement");
        this.openDepositTracking = Objects.requireNonNull(openDepositTracking, "openDepositTracking");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public void handle(ReservationStatusCommand command) {
        Objects.requireNonNull(command, "command");
        ReservationId reservationId = new ReservationId(command.reservationId());

        List<PlannedOperation> plan = policy.planFor(command.status());
        if (plan.isEmpty()) {
            return;
        }

        reservationLock.lock(reservationId);
        Optional<ReservationInformation> reservation = reservationInformation.findByReservationId(reservationId);
        if (reservation.isEmpty()) {
            record(reservationId, ErrorCode.RESERVATION_INFO_NOT_FOUND, "reservation_information not found");
            return;
        }
        if (!reservation.get().isCalculated()) {
            record(reservationId, ErrorCode.RESERVATION_INFO_INCOMPLETE,
                    "reservation_information has no calculated amounts");
            return;
        }

        for (PlannedOperation operation : plan) {
            processOperation(command, reservationId, operation);
        }
    }

    /**
     * Reclama y delega una operación. Un duplicado (misma identidad) se omite sin repetir el efecto.
     * Si la liquidación delegada devuelve {@code FAILED}, la excepción de dominio propaga y la
     * transacción revierte el mensaje completo: las dos operaciones de una cancelación moderada o
     * tardía se confirman o se revierten juntas (§Reglas 9, CE-004).
     */
    private void processOperation(ReservationStatusCommand command, ReservationId reservationId,
            PlannedOperation operation) {
        IdempotencyKey key = idempotencyKey(command);
        UUID logId = UUID.randomUUID();
        if (!statusLog.insertIfAbsent(logId, reservationId, command.status(), operation.operationType(), key,
                command.statusChangedAt())) {
            return;
        }
        delegate(command, reservationId, operation, key.value());
        if (isCompletedSettlement(operation)) {
            openDepositTracking(command, reservationId, key.value());
        }
        statusLog.markProcessed(logId, clock.instant(), OUTCOME_DELEGATED);
    }

    private void delegate(ReservationStatusCommand command, ReservationId reservationId,
            PlannedOperation operation, String operationKey) {
        switch (operation.operationType()) {
            case CHARGE -> processCharge.process(new ProcessChargeCommand(command.reservationId(),
                    command.paymentTokenRef(), command.paymentMethodType(), command.paymentMetadata(), operationKey));
            case REFUND -> requestRefund.request(new RefundRequestCommand(command.reservationId(),
                    RefundTrigger.valueOf(triggerOf(operation)), null, operationKey));
            case SETTLEMENT -> requestSettlement(reservationId, operation, operationKey);
        }
    }

    /**
     * La liquidación estándar de {@code COMPLETED} precede al seguimiento del depósito; las
     * cancelaciones se liquidan como compensación al propietario. Un resultado {@code FAILED} es un
     * fallo permanente del mensaje: se propaga para revertir la transacción completa.
     */
    private void requestSettlement(ReservationId reservationId, PlannedOperation operation,
            String operationKey) {
        SettlementTrigger trigger = SettlementTrigger.valueOf(triggerOf(operation));
        SettlementScope scope = trigger == SettlementTrigger.COMPLETED ? SettlementScope.STANDARD
                : SettlementScope.CANCELLATION_COMPENSATION;
        SettlementRequestResult result = requestSettlement.request(new SettlementCommand(reservationId, trigger,
                scope, null, operationKey));
        if (result.status() == SettlementStatus.FAILED) {
            throw new DomainException(
                    "settlement returned FAILED;trigger=" + trigger + ";operationKey=" + operationKey);
        }
    }

    /**
     * Abre el depósito retenido tras la liquidación. Un desenlace distinto de {@code HELD} significa
     * que el depósito ya estaba resuelto: se registra la inconsistencia y se conserva el existente
     * (RF-009B), sin revertir la liquidación del alquiler.
     */
    private void openDepositTracking(ReservationStatusCommand command, ReservationId reservationId,
            String operationKey) {
        DepositTrackingResult result = openDepositTracking.open(new OpenDepositTrackingCommand(reservationId,
                operationKey, DepositDispositionStatus.HELD, null, null, command.statusChangedAt()));
        if (result.status() != DepositDispositionStatus.HELD) {
            record(reservationId, ErrorCode.INTERNAL_ERROR,
                    "deposit_disposition=" + result.status() + ";operationKey=" + operationKey);
        }
    }

    private static boolean isCompletedSettlement(PlannedOperation operation) {
        return operation.operationType() == FinancialOperationType.SETTLEMENT
                && SettlementTrigger.COMPLETED.name().equals(operation.trigger());
    }

    private static String triggerOf(PlannedOperation operation) {
        if (operation.trigger() == null) {
            throw new IllegalStateException("Planned operation without trigger: " + operation.operationType());
        }
        return operation.trigger();
    }

    /**
     * Clave idempotente de la operación: por intento en {@code PENDING}, estable en los estados
     * terminales (D-UC07-11).
     */
    private static IdempotencyKey idempotencyKey(ReservationStatusCommand command) {
        String base = command.reservationId() + ":" + command.status();
        return command.status() == ReservationStatus.PENDING
                ? new IdempotencyKey(base + ":" + command.statusChangedAt())
                : new IdempotencyKey(base);
    }

    private void record(ReservationId reservationId, ErrorCode code, String detail) {
        failureRecorder.record(USE_CASE, reservationId.value(), code.name(), detail);
    }
}
