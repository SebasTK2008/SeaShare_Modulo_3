package com.seashare.seasharem3.domain.valueobject;

import com.seashare.seasharem3.domain.exception.DomainException;
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

        assertThatExceptionOfType(DomainException.class).isThrownBy(negativo::requireNonNegative);
    }

    @Test
    void rechazaImporteNoPositivoCuandoSeExigePositivo() {
        assertThatExceptionOfType(DomainException.class).isThrownBy(Money.ZERO::requirePositive);
        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> new Money(new BigDecimal("-1")).requirePositive());
    }

    @Test
    void aplicaPorcentajesConEscalaInterna4() {
        Money base = new Money(new BigDecimal("100000.00"));

        assertThat(base.percentageOf(new Percentage(new BigDecimal("10"))).amount())
                .isEqualByComparingTo(new BigDecimal("10000.0000"));
        assertThat(base.increaseBy(new Percentage(new BigDecimal("15"))).amount())
                .isEqualByComparingTo(new BigDecimal("115000.0000"));
        assertThat(base.percentageOf(Percentage.ZERO).amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void divideConRedondeoHalfUpYRechazaDivisorCero() {
        Money total = new Money(new BigDecimal("989999.99"));

        assertThat(total.divide(new BigDecimal("3")).amount()).isEqualByComparingTo(new BigDecimal("329999.9967"));
        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> total.divide(BigDecimal.ZERO));
    }

    @Test
    void comparaImportes() {
        Money menor = new Money(new BigDecimal("10.00"));
        Money mayor = new Money(new BigDecimal("20.00"));

        assertThat(menor.isLessThan(mayor)).isTrue();
        assertThat(menor.isLessThanOrEqualTo(mayor)).isTrue();
        assertThat(mayor.isGreaterThan(menor)).isTrue();
        assertThat(mayor.isGreaterThanOrEqualTo(menor)).isTrue();
        assertThat(menor.isPositive()).isTrue();
        assertThat(menor.isNegative()).isFalse();
        assertThat(Money.ZERO.isZero()).isTrue();
        assertThat(menor.min(mayor)).isEqualTo(menor);
        assertThat(menor.max(mayor)).isEqualTo(mayor);
        assertThat(menor.compareTo(mayor)).isNegative();
    }

    @Test
    void niegaYAplicaValorAbsoluto() {
        Money negativo = new Money(new BigDecimal("-15.50"));

        assertThat(negativo.negate().amount()).isEqualByComparingTo(new BigDecimal("15.5000"));
        assertThat(negativo.abs().amount()).isEqualByComparingTo(new BigDecimal("15.5000"));
    }
}
