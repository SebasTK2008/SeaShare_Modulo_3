package com.seashare.seasharem3.domain.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BaseRateExceptionTest {

    @Test
    void marksRetryableReasonsAsTransient() {
        assertThat(new BaseRateException(BaseRateException.BaseRateFailureReason.FLEET_UNAVAILABLE, "outage")
                .isTransient()).isTrue();
        assertThat(new BaseRateException(BaseRateException.BaseRateFailureReason.DYNAMIC_RATE_NOT_CONFIGURED, "missing")
                .isTransient()).isTrue();
        assertThat(new BaseRateException(BaseRateException.BaseRateFailureReason.PARAMETERS_UNAVAILABLE, "unreadable")
                .isTransient()).isTrue();
        assertThat(new BaseRateException(BaseRateException.BaseRateFailureReason.BASE_RATE_NOT_AVAILABLE, "no base rate")
                .isTransient()).isFalse();
    }

    @Test
    void exposesTheReason() {
        BaseRateException exception = new BaseRateException(
                BaseRateException.BaseRateFailureReason.BASE_RATE_NOT_AVAILABLE, "no base rate");

        assertThat(exception.reason()).isEqualTo(BaseRateException.BaseRateFailureReason.BASE_RATE_NOT_AVAILABLE);
    }
}
