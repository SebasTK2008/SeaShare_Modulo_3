package com.seashare.seasharem3.domain.valueobject;

import com.seashare.seasharem3.domain.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class BaseRateTest {

    @Test
    void aceptaTarifasMayoresQueCeroApoyandoseEnMoney() {
        BaseRate rate = new BaseRate(new Money(new BigDecimal("437500.00")));

        assertThat(rate.amount().amount()).isEqualByComparingTo(new BigDecimal("437500.0000"));
    }

    @Test
    void rechazaCeroYNegativos() {
        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> new BaseRate(new Money(BigDecimal.ZERO)));
        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> new BaseRate(new Money(new BigDecimal("-100.00"))));
    }

    @Test
    void rechazaNull() {
        assertThatNullPointerException().isThrownBy(() -> new BaseRate(null));
    }
}
