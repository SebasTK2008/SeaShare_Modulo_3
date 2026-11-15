package com.seashare.seasharem3.application.port.out;

import java.time.LocalDate;

/** Firma publicada en UC11 plan §Núcleo compartido: reloj de negocio (zona America/Bogota). */
public interface ClockPort {

    LocalDate today();
}
