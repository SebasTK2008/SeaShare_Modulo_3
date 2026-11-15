package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.GatewaySettlementResult;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.ReconcileSettlementIntentsUseCase;
import com.seashare.seasharem3.application.port.in.RegisterSettlementResultUseCase;
import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.application.port.out.SettlementCommandPublisher;
import com.seashare.seasharem3.application.port.out.SettlementIntentRepository;
import com.seashare.seasharem3.application.port.out.SettlementRecordRepository;
import com.seashare.seasharem3.domain.model.SettlementIntent;
import com.seashare.seasharem3.domain.valueobject.SettlementStatus;

import java.util.List;
import java.util.Objects;

/**
 * Concilia liquidaciones del libro interno simulado sin resultado definitivo.
 *
 * <p>No existe una pasarela remota que consultar: una intencion sin referencia externa se
 * republica si sigue en {@code PENDING_SEND} y, si ya avanzo sin referencia, se registra como fallo
 * una sola vez. Una intencion con referencia externa se repara reenviando el resultado simulado
 * deterministico; nunca se asume un exito sin esa referencia (D-UC10-10).</p>
 */
public class ReconcileSettlementIntentsService implements ReconcileSettlementIntentsUseCase {

    private static final String USE_CASE = "UC10";
    private static final String SIMULATED_STATUS = "approved";
    private static final String SIMULATED_DETAIL = "simulated internal settlement";
    private static final List<SettlementStatus> RECONCILABLE = List.of(
            SettlementStatus.PENDING_SEND, SettlementStatus.IN_PROCESS,
            SettlementStatus.COMMUNICATION_ERROR, SettlementStatus.COMPLETED);

    private final SettlementIntentRepository settlementIntentRepository;
    private final SettlementRecordRepository settlementRecordRepository;
    private final RegisterSettlementResultUseCase registerSettlementResult;
    private final FailureRecorderPort failureRecorder;
    private final SettlementCommandPublisher settlementCommandPublisher;

    public ReconcileSettlementIntentsService(SettlementIntentRepository settlementIntentRepository,
            SettlementRecordRepository settlementRecordRepository,
            RegisterSettlementResultUseCase registerSettlementResult, FailureRecorderPort failureRecorder) {
        this(settlementIntentRepository, settlementRecordRepository, registerSettlementResult, failureRecorder,
                id -> {
                });
    }

    public ReconcileSettlementIntentsService(SettlementIntentRepository settlementIntentRepository,
            SettlementRecordRepository settlementRecordRepository,
            RegisterSettlementResultUseCase registerSettlementResult, FailureRecorderPort failureRecorder,
            SettlementCommandPublisher settlementCommandPublisher) {
        this.settlementIntentRepository = Objects.requireNonNull(settlementIntentRepository,
                "settlementIntentRepository");
        this.settlementRecordRepository = Objects.requireNonNull(settlementRecordRepository,
                "settlementRecordRepository");
        this.registerSettlementResult = Objects.requireNonNull(registerSettlementResult, "registerSettlementResult");
        this.failureRecorder = Objects.requireNonNull(failureRecorder, "failureRecorder");
        this.settlementCommandPublisher = Objects.requireNonNull(settlementCommandPublisher,
                "settlementCommandPublisher");
    }

    @Override
    public void reconcile() {
        for (SettlementIntent intent : settlementIntentRepository.findByStatusIn(RECONCILABLE)) {
            if (isAlreadyAudited(intent)) {
                continue;
            }
            reconcile(intent);
        }
    }

    private boolean isAlreadyAudited(SettlementIntent intent) {
        return intent.status() == SettlementStatus.COMPLETED
                && settlementRecordRepository.findBySettlementIntentId(intent.id()).isPresent();
    }

    private void reconcile(SettlementIntent intent) {
        if (intent.externalReference() == null || intent.externalReference().isBlank()) {
            if (intent.status() == SettlementStatus.PENDING_SEND) {
                settlementCommandPublisher.publish(intent.id());
                return;
            }
            recordOnce(intent, "The settlement was never confirmed by the internal ledger");
            return;
        }
        // El libro interno es deterministico: la referencia externa ya guardada identifica una
        // liquidacion aprobada; se reenvia el resultado para reparar un registro incompleto.
        registerSettlementResult.register(new GatewaySettlementResult(SIMULATED_STATUS,
                intent.externalReference(), SIMULATED_DETAIL, intent.amount()));
    }

    private void recordOnce(SettlementIntent intent, String detail) {
        if (detail.equals(intent.statusDetail())) {
            return;
        }
        settlementIntentRepository.save(intent.withStatusDetail(detail));
        failureRecorder.record(USE_CASE, intent.reservationId().value(), ErrorCode.INTERNAL_ERROR.name(),
                "settlementIntentId=" + intent.id() + ";status=" + intent.status());
    }
}
