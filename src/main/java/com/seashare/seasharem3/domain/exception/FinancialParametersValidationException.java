package com.seashare.seasharem3.domain.exception;

import java.util.Objects;

/**
 * Error de validación de un campo concreto de los parámetros financieros globales.
 * El campo es el nombre de dominio ({@code insuranceFeePerPassenger}); el adaptador web lo traduce
 * al nombre JSON del contrato antes de publicarlo en {@code errors[]}.
 * Fuente: UC11 plan T004/T005 y contracts/README §3.3.
 */
public class FinancialParametersValidationException extends DomainException {

    private final String field;

    public FinancialParametersValidationException(String field, String message) {
        super(message);
        this.field = Objects.requireNonNull(field, "field");
    }

    public String field() {
        return field;
    }
}
