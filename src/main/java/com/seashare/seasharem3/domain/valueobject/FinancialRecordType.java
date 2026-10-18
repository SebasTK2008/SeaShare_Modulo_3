package com.seashare.seasharem3.domain.valueobject;

/**
 * Tipo de registro financiero inmutable expuesto por la vista {@code v_financial_records}.
 * Fuente: contratos rest/UC12-consultar-registros-financieros.md §2 y rest/UC13-consultar-informe-financiero.md §4
 * (general-plan D-18: valores de enumeración en inglés). [CONV] núcleo compartido.
 */
public enum FinancialRecordType {
    CHARGE,
    REFUND,
    SETTLEMENT,
    COMMISSION
}
