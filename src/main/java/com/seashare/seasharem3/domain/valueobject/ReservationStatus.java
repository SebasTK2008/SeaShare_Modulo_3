package com.seashare.seasharem3.domain.valueobject;

/** Los 10 estados notificados por Reservas y reconocidos por UC07. Fuente: contrato events/UC07-estado-reserva.md §3 regla 1. */
public enum ReservationStatus {
    AVAILABLE,
    INITIATED,
    RESERVED,
    IN_NAVIGATION,
    PENDING,
    CANCELLED_FLEXIBLE,
    CANCELLED_MODERATE,
    CANCELLED_STRICT,
    CANCELLED_BY_HOST,
    COMPLETED
}
