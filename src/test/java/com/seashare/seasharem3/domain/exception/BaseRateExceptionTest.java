package com.seashare.seasharem3.domain.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BaseRateExceptionTest {

    @Test
    void marcaComoTransitoriosLosMotivosReintentables() {
        assertThat(new BaseRateException(BaseRateException.BaseRateFailureReason.FLEET_UNAVAILABLE, "caida")
                .isTransient()).isTrue();
        assertThat(new BaseRateException(BaseRateException.BaseRateFailureReason.DYNAMIC_RATE_NOT_CONFIGURED, "falta")
                .isTransient()).isTrue();
        assertThat(new BaseRateException(BaseRateException.BaseRateFailureReason.BASE_RATE_NOT_AVAILABLE, "sin tarifa")
                .isTransient()).isFalse();
    }

    @Test
    void exponeElMotivo() {
        BaseRateException exception = new BaseRateException(
                BaseRateException.BaseRateFailureReason.BASE_RATE_NOT_AVAILABLE, "sin tarifa");

        assertThat(exception.reason()).isEqualTo(BaseRateException.BaseRateFailureReason.BASE_RATE_NOT_AVAILABLE);
    }
}
