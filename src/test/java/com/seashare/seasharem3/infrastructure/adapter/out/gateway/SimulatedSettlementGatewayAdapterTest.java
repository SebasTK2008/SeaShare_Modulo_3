package com.seashare.seasharem3.infrastructure.adapter.out.gateway;

import com.seashare.seasharem3.application.dto.GatewaySettlementRequest;
import com.seashare.seasharem3.application.dto.GatewaySettlementResult;
import com.seashare.seasharem3.domain.valueobject.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/** Libro interno simulado de UC10: aprueba siempre y su referencia es determinista por intento. */
class SimulatedSettlementGatewayAdapterTest {

    private static final Money AMOUNT = new Money(new BigDecimal("660000.00"));

    private final SimulatedSettlementGatewayAdapter adapter = new SimulatedSettlementGatewayAdapter();

    @Test
    void approvesWithAReferenceDerivedFromTheIdempotencyKey() {
        String key = UUID.randomUUID().toString();

        GatewaySettlementResult result = adapter.send(new GatewaySettlementRequest(UUID.randomUUID(), AMOUNT, key));

        assertThat(result.status()).isEqualTo("approved");
        assertThat(result.externalId()).isEqualTo("sim-settlement-" + key);
        assertThat(result.statusDetail()).isEqualTo("simulated internal settlement");
        assertThat(result.amount()).isEqualTo(AMOUNT);
    }

    @Test
    void aRetryWithTheSameKeyGetsTheSameReferenceAndANewKeyAnotherOne() {
        String key = UUID.randomUUID().toString();
        UUID reservation = UUID.randomUUID();

        GatewaySettlementResult first = adapter.send(new GatewaySettlementRequest(reservation, AMOUNT, key));
        GatewaySettlementResult retry = adapter.send(new GatewaySettlementRequest(reservation, AMOUNT, key));
        GatewaySettlementResult other = adapter.send(new GatewaySettlementRequest(reservation, AMOUNT,
                UUID.randomUUID().toString()));

        assertThat(retry.externalId()).isEqualTo(first.externalId());
        assertThat(other.externalId()).isNotEqualTo(first.externalId());
    }

    @Test
    void rejectsAMissingRequestOrKey() {
        assertThatNullPointerException().isThrownBy(() -> adapter.send(null));
        assertThatNullPointerException().isThrownBy(() -> adapter.send(
                new GatewaySettlementRequest(UUID.randomUUID(), AMOUNT, null)));
    }
}
