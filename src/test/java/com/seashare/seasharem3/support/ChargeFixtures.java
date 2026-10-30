package com.seashare.seasharem3.support;

import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.ChargeIntentStatus;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.PaymentMethodReference;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.domain.valueobject.ReservationValue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Datos de prueba compartidos por los tests de UC05: una reserva con el valor total calculado por
 * UC04 y las intenciones de cobro en sus distintos estados.
 * [CONV] núcleo compartido para las pruebas de UC05.
 */
public final class ChargeFixtures {

    /** Valor total de ejemplo: alquiler 900000 + seguro 60000 + deposito 30000. */
    public static final Money TOTAL = new Money(new BigDecimal("990000.00"));

    public static final Instant CREATED_AT = Instant.parse("2026-10-20T15:04:05Z");

    private ChargeFixtures() {
    }

    /** Reserva con el desglose calculado y escrito por UC04. */
    public static ReservationInformation calculatedReservation() {
        ReservationInformation information = ReservationInformation.create(new ReservationId(UUID.randomUUID()),
                new BoatId(UUID.randomUUID()), new BaseRate(new Money(new BigDecimal("350000.00"))), null,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10), 2, new OwnerId(UUID.randomUUID()), 4);
        return information.withCalculated(new ReservationValue(new Money(new BigDecimal("900000.00")),
                new Money(new BigDecimal("60000.00")), new Money(new BigDecimal("30000.00")), TOTAL),
                UUID.randomUUID(), CREATED_AT);
    }

    /** Intencion de cobro recien registrada, en {@code PENDING_SEND}. */
    public static ChargeIntent pendingIntent(ReservationInformation reservation) {
        return ChargeIntent.create(UUID.randomUUID(), reservation.reservationId(),
                new IdempotencyKey("reservation|PENDING|2026-10-20T15:04:05Z"), reservation.totalAmount(),
                new PaymentMethodReference("token-123", "CREDIT_CARD", "4242"), CREATED_AT);
    }

    /**
     * Intencion de cobro que ya tiene la referencia externa del pago en la pasarela y espera su
     * resultado: es el estado en el que recibe la respuesta tecnica del worker o el webhook.
     */
    public static ChargeIntent pendingIntentWithReference(ReservationInformation reservation,
            String externalReference) {
        return pendingIntent(reservation).withExternalReference(externalReference);
    }

    /** Intencion de cobro capturada, con la referencia externa del pago en la pasarela. */
    public static ChargeIntent capturedIntent(ReservationInformation reservation, String externalReference) {
        return pendingIntent(reservation)
                .transitionTo(ChargeIntentStatus.IN_PROCESS)
                .transitionTo(ChargeIntentStatus.CAPTURED)
                .withExternalReference(externalReference);
    }

    /**
     * Intencion de cobro en cualquier estado interno, con detalle y referencia externa opcionales.
     * Se usa en las pruebas de consulta de UC06, que necesitan todos los estados sin recorrer las
     * transiciones de UC05.
     */
    public static ChargeIntent intentWithStatus(ReservationInformation reservation, ChargeIntentStatus status,
            String externalReference, String statusDetail, Instant createdAt) {
        return ChargeIntent.restore(UUID.randomUUID(), reservation.reservationId(),
                new IdempotencyKey("intent|" + status + "|" + createdAt), status, TOTAL,
                new PaymentMethodReference("token-123", "CREDIT_CARD", "4242"), externalReference, statusDetail,
                createdAt);
    }
}
