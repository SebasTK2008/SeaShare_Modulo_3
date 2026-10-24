package com.seashare.seasharem3.domain.valueobject;

/**
 * Referencia no sensible del medio de pago: token, tipo y últimos 4 dígitos.
 * Fuente: UC05 plan T003 (token + tipo + últimos 4); contrato de cobro §2.
 */
public record PaymentMethodReference(String paymentTokenRef, String paymentMethodType, String lastFour) {
}
