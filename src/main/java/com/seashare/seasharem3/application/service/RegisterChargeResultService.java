package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.ChargeGatewayResult;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.RegisterChargeResultUseCase;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.application.port.out.ChargeRecordQueryPort;
import com.seashare.seasharem3.application.port.out.ChargeRecordRepository;
import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.application.port.out.ReservationInformationRepository;
import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.model.ChargeRecord;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.ChargeIntentStatus;

import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta UC05/HU2: registra el resultado reportado por la pasarela sobre la intencion de cobro y,
 * cuando el cobro queda capturado, crea el registro de auditoria inmutable.
 *
 * <p>La respuesta tecnica del worker y el webhook convergen aqui (D-UC05-08): el resultado se
 * busca por la referencia externa del pago ({@code data.id}) y el estado externo se traduce al
 * estado interno. Un resultado repetido con el mismo estado no altera nada, y un resultado sin
 * intencion asociada se registra como fallo operativo sin crear registro de cobro.</p>
 *
 * <p>El registro de auditoria se crea solo en {@code CAPTURED}, nunca para un rechazo, una
 * cancelacion, una expiracion, un cobro en proceso ni una falla de comunicacion (RF-008, CE-004),
 * y en la misma transaccion que la actualizacion de la intencion (regla 6). Si el registro no puede
 * crearse porque la reserva ya no tiene el desglose calculado, el cobro esta confirmado por la
 * pasarela: se registra el fallo y se conserva la intencion en {@code CAPTURED} en vez de perder el
 * resultado.</p>
 *
 * <p>Fuente: UC05 plan T017, T018 y reglas 5, 6 y 8; SPEC RF-005, RF-006, RF-008, RF-014,
 * CE-001, CE-004 y CE-007.</p>
 */
public class RegisterChargeResultService implements RegisterChargeResultUseCase {

    private static final String USE_CASE = "UC05";

    private final ChargeIntentRepository chargeIntentRepository;
    private final ChargeRecordRepository chargeRecordRepository;
    private final ChargeRecordQueryPort chargeRecordQueryPort;
    private final ReservationInformationRepository reservationInformationRepository;
    private final FailureRecorderPort failureRecorder;
    private final Clock instantClock;

    public RegisterChargeResultService(ChargeIntentRepository chargeIntentRepository,
            ChargeRecordRepository chargeRecordRepository, ChargeRecordQueryPort chargeRecordQueryPort,
            ReservationInformationRepository reservationInformationRepository, FailureRecorderPort failureRecorder,
            Clock instantClock) {
        this.chargeIntentRepository = Objects.requireNonNull(chargeIntentRepository, "chargeIntentRepository");
        this.chargeRecordRepository = Objects.requireNonNull(chargeRecordRepository, "chargeRecordRepository");
        this.chargeRecordQueryPort = Objects.requireNonNull(chargeRecordQueryPort, "chargeRecordQueryPort");
        this.reservationInformationRepository = Objects.requireNonNull(reservationInformationRepository,
                "reservationInformationRepository");
        this.failureRecorder = Objects.requireNonNull(failureRecorder, "failureRecorder");
        this.instantClock = Objects.requireNonNull(instantClock, "instantClock");
    }

    @Override
    @Transactional
    public void register(ChargeGatewayResult result) {
        Objects.requireNonNull(result, "result");

        ChargeIntent intent = findIntent(result.externalId());
        if (intent == null) {
            failureRecorder.record(USE_CASE, null, ErrorCode.CHARGE_INTENT_NOT_FOUND.name(), payloadRef(result));
            return;
        }

        ChargeIntentStatus status = toStatus(result.status());
        if (status == null) {
            failureRecorder.record(USE_CASE, intent.reservationId().value(), ErrorCode.INTERNAL_ERROR.name(),
                    payloadRef(result));
            return;
        }
        if (!intent.canTransitionTo(status)) {
            failureRecorder.record(USE_CASE, intent.reservationId().value(), ErrorCode.INTERNAL_ERROR.name(),
                    payloadRef(result));
            return;
        }

        ChargeIntent updated = intent.transitionTo(status);
        if (result.statusDetail() != null) {
            updated = updated.withStatusDetail(result.statusDetail());
        }
        if (!updated.equals(intent)) {
            chargeIntentRepository.save(updated);
        }
        if (status == ChargeIntentStatus.CAPTURED) {
            createAuditRecord(updated);
        }
    }

    /** El resultado se asocia a su intencion por la referencia externa del pago (regla 8). */
    private ChargeIntent findIntent(String externalReference) {
        if (externalReference == null || externalReference.isBlank()) {
            return null;
        }
        return chargeIntentRepository.findByExternalReference(externalReference).orElse(null);
    }

    /**
     * Crea el registro de auditoria del cobro capturado. Un registro ya existente hace que el
     * resultado repetido sea idempotente; si la reserva ya no tiene el desglose calculado, el
     * cobro confirmado no se pierde: se registra el fallo y la intencion permanece {@code CAPTURED}.
     */
    private void createAuditRecord(ChargeIntent capturedIntent) {
        if (chargeRecordQueryPort.findByChargeIntentId(capturedIntent.id()).isPresent()) {
            return;
        }
        Optional<ReservationInformation> reservation = reservationInformationRepository
                .findByReservationId(capturedIntent.reservationId());
        if (reservation.isEmpty()) {
            failureRecorder.record(USE_CASE, capturedIntent.reservationId().value(),
                    ErrorCode.RESERVATION_INFO_NOT_FOUND.name(), payloadRef(capturedIntent));
            return;
        }
        try {
            chargeRecordRepository.save(ChargeRecord.create(UUID.randomUUID(), capturedIntent, reservation.get(),
                    instantClock.instant()));
        } catch (DomainException exception) {
            failureRecorder.record(USE_CASE, capturedIntent.reservationId().value(),
                    ErrorCode.INTERNAL_ERROR.name(), payloadRef(capturedIntent));
        }
    }

    /**
     * Traduce el estado externo de la pasarela al estado interno. Con cobro directo y captura
     * automatica (general-plan D-32) la aprobacion de la pasarela es el cobro: {@code approved}
     * se traduce a {@code CAPTURED}. Un estado desconocido no altera la intencion.
     */
    private static ChargeIntentStatus toStatus(String externalStatus) {
        if (externalStatus == null) {
            return null;
        }
        return switch (externalStatus.toLowerCase(Locale.ROOT)) {
            case "approved" -> ChargeIntentStatus.CAPTURED;
            case "in_process" -> ChargeIntentStatus.IN_PROCESS;
            case "rejected" -> ChargeIntentStatus.REJECTED;
            case "cancelled" -> ChargeIntentStatus.CANCELLED;
            case "expired" -> ChargeIntentStatus.EXPIRED;
            default -> null;
        };
    }

    private static String payloadRef(ChargeGatewayResult result) {
        return "externalReference=" + result.externalId() + ";status=" + result.status();
    }

    private static String payloadRef(ChargeIntent intent) {
        return "chargeIntentId=" + intent.id() + ";reservationId=" + intent.reservationId().value();
    }
}
