package com.seashare.seasharem3.domain.valueobject;

/** Tipo de operación financiera disparada por UC07. Fuente: UC07 plan T002 y §Reglas 8 [CONV]. */
public enum FinancialOperationType {
    CHARGE,
    REFUND,
    SETTLEMENT
}
