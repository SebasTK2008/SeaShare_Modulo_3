package com.seashare.seasharem3.domain.valueobject;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DomainIdsTest {

    @Test
    void envuelvenUuidYRespetanLaIgualdadPorValor() {
        UUID uuid = UUID.randomUUID();

        assertThat(new ReservationId(uuid)).isEqualTo(new ReservationId(uuid));
        assertThat(new BoatId(uuid).value()).isEqualTo(uuid);
        assertThat(new OwnerId(uuid)).isEqualTo(new OwnerId(uuid));
        assertThat(new DisputeId(uuid)).isEqualTo(new DisputeId(uuid));
    }

    @Test
    void rechazanNull() {
        assertThatNullPointerException().isThrownBy(() -> new ReservationId(null));
        assertThatNullPointerException().isThrownBy(() -> new BoatId(null));
        assertThatNullPointerException().isThrownBy(() -> new OwnerId(null));
        assertThatNullPointerException().isThrownBy(() -> new DisputeId(null));
    }

    @Test
    void laClaveIdempotenteRechazaVacios() {
        assertThat(new IdempotencyKey("op-key").value()).isEqualTo("op-key");
        assertThatNullPointerException().isThrownBy(() -> new IdempotencyKey(null));
        org.assertj.core.api.Assertions.assertThatExceptionOfType(com.seashare.seasharem3.domain.exception.DomainException.class)
                .isThrownBy(() -> new IdempotencyKey("  "));
    }
}
