package com.seashare.seasharem3.domain.exception;

/**
 * No hay configuración financiera vigente, o el valor requerido está ausente o nulo
 * (catálogo README §3.4: {@code FINANCIAL_PARAMETERS_NOT_CONFIGURED}, HTTP 503).
 * Fuente: UC01 plan §Reglas 14 y UC04 (lectura de parámetros); nombre [CONV] del núcleo compartido.
 */
public class FinancialParametersNotConfiguredException extends DomainException {

    public FinancialParametersNotConfiguredException(String message) {
        super(message);
    }
}
