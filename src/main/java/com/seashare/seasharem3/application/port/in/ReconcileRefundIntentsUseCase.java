package com.seashare.seasharem3.application.port.in;

/**
 * Puerto propio de UC09 para el job de conciliación (D-UC09-05).
 * Fuente: UC09 plan T006/T023.
 * GAP: firma no publicada.
 */
public interface ReconcileRefundIntentsUseCase {

    void reconcile();
}
