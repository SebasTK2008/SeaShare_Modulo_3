package com.seashare.seasharem3.domain.valueobject;

/** Resultado puro del calculo de reembolso: monto solicitado y alcance. Fuente: UC09 plan T004. */
public record RefundCalculation(Money amount, RefundScope scope) {
}
