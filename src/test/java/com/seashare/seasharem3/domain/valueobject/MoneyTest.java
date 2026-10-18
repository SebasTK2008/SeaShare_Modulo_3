package com.seashare.seasharem3.domain.valueobject;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class MoneyTest {

    @Test
    void normalizaLaEscalaInternaA4Decimales() {
        Money money = new Money(new BigDecimal("350000"));

        assertThat(money.amount()).isEqualByComparingTo(new BigDecimal("350000.0000"));
        assertThat(money.amount().scale()).isEqualTo(4);
    }

    @Test
    void sumaRestaYMultiplicaConservandoLaEscala4() {
        Money tarifa = new Money(new BigDecimal("350000.00"));
        Money seguro = new Money(new BigDecimal("15000.00"));

        assertThat(tarifa.add(seguro).amount()).isEqualByComparingTo(new BigDecimal("365000.0000"));
        assertThat(tarifa.subtract(seguro).amount()).isEqualByComparingTo(new BigDecimal("335000.0000"));
        assertThat(tarifa.multiply(new BigDecimal("3")).amount()).isEqualByComparingTo(new BigDecimal("1050000.0000"));
        assertThat(seguro.multiply(new BigDecimal("4")).amount()).isEqualByComparingTo(new BigDecimal("60000.0000"));
    }

    @Test
    void salidaMonetariaRedondeaHalfUpA2Decimales() {
        Money conFraccion = new Money(new BigDecimal("437500.1250"));

        assertThat(conFraccion.toOutputAmount()).isEqualByComparingTo(new BigDecimal("437500.13"));
        assertThat(conFraccion.toOutputAmount().scale()).isEqualTo(2);
    }

    @Test
    void rechazaNull() {
        assertThatNullPointerException().isThrownBy(() -> new Money(null));
    }

    @Test
    void rechazaMultiplicadorNull() {
        assertThatNullPointerException().isThrownBy(() -> new Money(BigDecimal.TEN).multiply(null));
    }

    @Test
    void rechazaImporteNegativoCuandoSeExigeNoNegativo() {
        Money negativo = new Money(new BigDecimal("-0.01"));

        assertThatExceptionOfType(com.seashare.seasharem3.domain.exception.DomainException.class)
                .isThrownBy(negativo::requireNonNegative);
    }
}
