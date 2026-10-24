package com.seashare.seasharem3.support;

import com.seashare.seasharem3.application.port.out.ClockPort;

import java.time.LocalDate;

/**
 * Fake de {@link ClockPort} con fecha fija y movible. [CONV] núcleo compartido: disponible para las
 * pruebas de todos los UC (el UC dueño ajusta la fecha con {@link #moveTo(LocalDate)}).
 */
public class FixedClockPort implements ClockPort {

    private LocalDate today;

    public FixedClockPort(LocalDate today) {
        this.today = today;
    }

    public void moveTo(LocalDate today) {
        this.today = today;
    }

    @Override
    public LocalDate today() {
        return today;
    }
}
