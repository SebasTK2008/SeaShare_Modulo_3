package com.seashare.seasharem3.domain.valueobject;

import com.seashare.seasharem3.domain.exception.DomainException;

import java.util.Objects;

/**
 * Referencia no sensible del medio de pago: token, tipo y últimos 4 dígitos.
 * Fuente: UC05 plan T003 (token + tipo + últimos 4); contrato de cobro §2.
 *
 * <p>El {@code toString()} no muestra el token: el token no se escribe en logs (regla 7, CE-005).</p>
 */
public record PaymentMethodReference(String paymentTokenRef, String paymentMethodType, String lastFour) {

    /** Longitud máxima de los últimos dígitos que admite el esquema ({@code last_four VARCHAR(4)}). */
    private static final int MAX_LAST_FOUR_LENGTH = 4;

    public PaymentMethodReference {
        Objects.requireNonNull(paymentTokenRef, "paymentTokenRef");
        if (paymentTokenRef.isBlank()) {
            throw new DomainException("The payment token reference cannot be blank");
        }
        if (lastFour != null && !lastFour.matches("\\d{1," + MAX_LAST_FOUR_LENGTH + "}")) {
            throw new DomainException(
                    "The last four digits must be at most " + MAX_LAST_FOUR_LENGTH + " digits: " + lastFour);
        }
    }

    /** Enmascara el token: solo se muestran el tipo de medio y los últimos dígitos (CE-005). */
    @Override
    public String toString() {
        return "PaymentMethodReference[paymentMethodType=" + paymentMethodType + ", lastFour=" + lastFour + "]";
    }
}
