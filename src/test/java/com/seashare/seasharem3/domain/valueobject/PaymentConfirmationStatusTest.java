package com.seashare.seasharem3.domain.valueobject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas del mapeo de estados internos de la intención al vocabulario de UC06.
 * Fuente: UC06 plan T002; general-plan §8.
 */
class PaymentConfirmationStatusTest {

    @ParameterizedTest
    @CsvSource({
            "PENDING_SEND, IN_PROCESS",
            "IN_PROCESS, IN_PROCESS",
            "CAPTURED, APPROVED",
            "REJECTED, REJECTED",
            "CANCELLED, CANCELLED",
            "EXPIRED, EXPIRED",
            "COMMUNICATION_ERROR, UNKNOWN"
    })
    void mapsEveryInternalStatusToTheContractStatus(ChargeIntentStatus internal,
            PaymentConfirmationStatus expected) {
        assertThat(PaymentConfirmationStatus.from(internal)).isEqualTo(expected);
    }

    @Test
    void rejectsANullInternalStatus() {
        assertThatThrownBy(() -> PaymentConfirmationStatus.from(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("status");
    }
}
