package com.seashare.seasharem3.domain.valueobject;

/** Desglose interno usado para calcular un reembolso. Fuente: charge_record o reservation_information. */
public record RefundBreakdown(Money rentalAmount, Money insuranceAmount, Money depositAmount, Money totalAmount) {
}
