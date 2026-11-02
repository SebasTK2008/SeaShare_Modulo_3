package com.seashare.seasharem3.infrastructure.adapter.out.persistence.charge;

import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.valueobject.ChargeIntentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Mapeo de {@code charge_intent} (general-plan §4; migración V1, líneas 147-173). Admite varias
 * intenciones por reserva: la unicidad la da {@code UNIQUE(idempotency_key)}, no la reserva
 * (RF-004A, D-28). No sale del adaptador.
 *
 * <p>{@code requested_amount} no se mapea: el dominio todavía no modela el monto solicitado
 * por separado del monto del cobro (GAP documentado en el esquema).
 */
@Entity
@Table(name = "charge_intent")
public class ChargeIntentJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "payment_token_ref", nullable = false)
    private String paymentTokenRef;

    @Column(name = "payment_method_type")
    private String paymentMethodType;

    @Column(name = "payer_email")
    private String payerEmail;

    @Column(name = "last_four", length = 4)
    private String lastFour;

    @Column(name = "external_reference")
    private String externalReference;

    @Column(name = "status_detail")
    private String statusDetail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected ChargeIntentJpaEntity() {
    }

    /** Entidad nueva con todos los datos de la intención (alta del intento). */
    static ChargeIntentJpaEntity from(ChargeIntent intent) {
        ChargeIntentJpaEntity entity = new ChargeIntentJpaEntity();
        entity.id = intent.id();
        entity.reservationId = intent.reservationId().value();
        entity.idempotencyKey = intent.idempotencyKey().value();
        entity.amount = intent.amount().amount();
        entity.paymentTokenRef = intent.paymentMethod().paymentTokenRef();
        entity.paymentMethodType = intent.paymentMethod().paymentMethodType();
        entity.lastFour = intent.paymentMethod().lastFour();
        entity.payerEmail = intent.payerEmail();
        entity.createdAt = intent.createdAt();
        entity.apply(intent);
        return entity;
    }

    /**
     * Copia el estado operativo de la intención: el estado, la referencia externa del pago y el
     * detalle técnico publicado por la pasarela. El resto de los datos (reserva, clave idempotente,
     * monto y medio de pago) son inmutables durante el intento.
     */
    void apply(ChargeIntent intent) {
        this.status = intent.status().name();
        this.externalReference = intent.externalReference();
        this.statusDetail = intent.statusDetail();
    }

    UUID getId() {
        return id;
    }

    UUID getReservationId() {
        return reservationId;
    }

    String getIdempotencyKey() {
        return idempotencyKey;
    }

    String getStatus() {
        return status;
    }

    BigDecimal getAmount() {
        return amount;
    }

    String getPaymentTokenRef() {
        return paymentTokenRef;
    }

    String getPaymentMethodType() {
        return paymentMethodType;
    }

    String getPayerEmail() {
        return payerEmail;
    }

    String getLastFour() {
        return lastFour;
    }

    String getExternalReference() {
        return externalReference;
    }

    String getStatusDetail() {
        return statusDetail;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    ChargeIntentStatus statusValue() {
        return ChargeIntentStatus.valueOf(status);
    }
}
