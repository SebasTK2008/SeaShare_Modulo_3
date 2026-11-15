package com.seashare.seasharem3.domain.model;

import com.seashare.seasharem3.domain.valueobject.DepositDispositionStatus;
import com.seashare.seasharem3.domain.valueobject.DisputeStatus;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Seguimiento del desenlace unico del deposito de garantia (UC08).
 * Fuente: general-plan §4 y D-34; UC08 plan T008/T008a.
 *
 * <p>La fila se crea al completar la reserva en HELD y solo puede cambiar una vez a
 * REFUND_REQUESTED o SETTLEMENT_REQUESTED. El cambio real lo aplica el repositorio con un
 * compare-and-set atomico.</p>
 */
public final class DepositDisposition {

    private final ReservationId reservationId;
    private final Instant completedAt;
    private final UUID disputeId;
    private final DisputeStatus disputeStatus;
    private final DepositDispositionStatus disposition;

    private DepositDisposition(ReservationId reservationId, Instant completedAt, UUID disputeId,
            DisputeStatus disputeStatus, DepositDispositionStatus disposition) {
        this.reservationId = Objects.requireNonNull(reservationId, "reservationId");
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt");
        this.disputeId = disputeId;
        this.disputeStatus = disputeStatus;
        this.disposition = Objects.requireNonNull(disposition, "disposition");
    }

    public static DepositDisposition create(ReservationId reservationId, Instant completedAt, UUID disputeId,
            DisputeStatus disputeStatus, DepositDispositionStatus disposition) {
        return new DepositDisposition(reservationId, completedAt, disputeId, disputeStatus, disposition);
    }

    /** Rehidrata una fila ya persistida; la base de datos es la fuente de verdad. */
    public static DepositDisposition restore(ReservationId reservationId, Instant completedAt, UUID disputeId,
            DisputeStatus disputeStatus, DepositDispositionStatus disposition) {
        return new DepositDisposition(reservationId, completedAt, disputeId, disputeStatus, disposition);
    }

    public ReservationId reservationId() {
        return reservationId;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public UUID disputeId() {
        return disputeId;
    }

    public DisputeStatus disputeStatus() {
        return disputeStatus;
    }

    public DepositDispositionStatus disposition() {
        return disposition;
    }
}
