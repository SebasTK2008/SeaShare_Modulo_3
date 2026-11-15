package com.seashare.seasharem3.application.port.in;

/**
 * Puerto propio de UC10 para el job de conciliación de intenciones (reintenta las que quedaron en
 * {@code COMMUNICATION_ERROR}). Fuente: UC10 plan T012a/T013 y §Puerto interno (firma congelada).
 */
public interface ReconcileSettlementIntentsUseCase {

    void reconcile();
}
