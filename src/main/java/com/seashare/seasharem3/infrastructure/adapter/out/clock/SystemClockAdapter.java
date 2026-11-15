package com.seashare.seasharem3.infrastructure.adapter.out.clock;

import com.seashare.seasharem3.application.port.out.ClockPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Reloj de negocio en la zona configurada ({@code seashare.timezone}, America/Bogota).
 * Fuente: general-plan §7.4 y D-12; UC11 plan §Núcleo compartido.
 */
@Component
public class SystemClockAdapter implements ClockPort {

    private final ZoneId zoneId;

    public SystemClockAdapter(@Value("${seashare.timezone:America/Bogota}") String timezone) {
        this.zoneId = ZoneId.of(timezone);
    }

    @Override
    public LocalDate today() {
        return LocalDate.now(zoneId);
    }
}
