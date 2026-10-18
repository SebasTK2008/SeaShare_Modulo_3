package com.seashare.seasharem3.infrastructure.adapter.out.clock;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class SystemClockAdapterTest {

    @Test
    void hoyUsaLaZonaConfigurada() {
        SystemClockAdapter adapter = new SystemClockAdapter("America/Bogota");

        assertThat(adapter.today()).isEqualTo(LocalDate.now(ZoneId.of("America/Bogota")));
    }

    @Test
    void unaZonaDesconocidaFallaAlConstruir() {
        assertThatExceptionOfType(ZoneRulesException.class)
                .isThrownBy(() -> new SystemClockAdapter("No/Existe"));
    }
}
