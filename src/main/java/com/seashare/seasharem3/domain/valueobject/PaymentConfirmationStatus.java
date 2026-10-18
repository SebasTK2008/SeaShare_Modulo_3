package com.seashare.seasharem3.domain.valueobject;

/** Estados devueltos por la consulta de confirmación de pago. Fuente: contrato rest/UC06-confirmacion-pago.md §4. */
public enum PaymentConfirmationStatus {
    IN_PROCESS,
    APPROVED,
    REJECTED,
    CANCELLED,
    EXPIRED,
    UNKNOWN
}
