package com.seashare.seasharem3.infrastructure.adapter.out.persistence.lock;

import com.seashare.seasharem3.application.port.out.ReservationLockPort;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Bloqueo asesor de PostgreSQL por reserva ({@code pg_advisory_xact_lock}, D-15). [CONV] se usa la
 * variante de dos enteros con los 32 bits bajos de cada mitad del UUID (colisión = bloqueo más grueso,
 * nunca menos protección). El bloqueo se libera al terminar la transacción del llamador: el adaptador
 * debe invocarse dentro de una transacción activa.
 */
@Component
public class PostgresReservationLockAdapter implements ReservationLockPort {

    private final JdbcTemplate jdbcTemplate;

    public PostgresReservationLockAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void lock(ReservationId reservationId) {
        UUID value = reservationId.value();
        jdbcTemplate.queryForObject("SELECT pg_advisory_xact_lock(?, ?)", (rs, rowNum) -> null,
                (int) value.getMostSignificantBits(), (int) value.getLeastSignificantBits());
    }
}
