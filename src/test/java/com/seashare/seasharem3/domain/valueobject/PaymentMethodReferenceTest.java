package com.seashare.seasharem3.domain.valueobject;

import com.seashare.seasharem3.domain.exception.DomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class PaymentMethodReferenceTest {

    @Test
    void keepsTheTokenTypeAndLastFour() {
        PaymentMethodReference reference = new PaymentMethodReference("token-123", "credit_card", "4242");

        assertThat(reference.paymentTokenRef()).isEqualTo("token-123");
        assertThat(reference.paymentMethodType()).isEqualTo("credit_card");
        assertThat(reference.lastFour()).isEqualTo("4242");
    }

    @Test
    void masksTheTokenInItsTextForm() {
        PaymentMethodReference reference = new PaymentMethodReference("token-123", "credit_card", "4242");

        assertThat(reference.toString()).doesNotContain("token-123");
        assertThat(reference.toString()).contains("credit_card").contains("4242");
    }

    @Test
    void comparesByValue() {
        PaymentMethodReference reference = new PaymentMethodReference("token-123", "credit_card", "4242");
        PaymentMethodReference same = new PaymentMethodReference("token-123", "credit_card", "4242");

        assertThat(reference).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(reference).isNotEqualTo(new PaymentMethodReference("token-456", "credit_card", "4242"));
    }

    @Test
    void rejectsABlankToken() {
        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> new PaymentMethodReference("  ", "credit_card", "4242"));
    }

    @Test
    void rejectsLastFourDigitsThatDoNotFitTheColumn() {
        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> new PaymentMethodReference("token-123", "credit_card", "42421"));
        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> new PaymentMethodReference("token-123", "credit_card", "42a2"));
    }

    @Test
    void acceptsAMissingTypeAndLastFour() {
        PaymentMethodReference reference = new PaymentMethodReference("token-123", null, null);

        assertThat(reference.paymentMethodType()).isNull();
        assertThat(reference.lastFour()).isNull();
    }
}
