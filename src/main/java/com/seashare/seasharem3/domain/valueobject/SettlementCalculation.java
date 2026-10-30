package com.seashare.seasharem3.domain.valueobject;

/** Resultado puro del calculo de liquidacion (UC10 plan T004). */
public record SettlementCalculation(Money disbursedAmount, Money commissionAmount, Money rentalGrossAmount,
                                    Money insuranceAmount, Money depositAmount) {
}
