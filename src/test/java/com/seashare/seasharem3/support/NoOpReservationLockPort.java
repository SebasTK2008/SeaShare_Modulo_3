package com.seashare.seasharem3.support;

import com.seashare.seasharem3.application.port.out.ReservationLockPort;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.util.ArrayList;
import java.util.List;

/**
 * Fake no-op de {@link ReservationLockPort}: registra las reservas bloqueadas para poder afirmar sobre
 * ellas. En memoria no hay concurrencia que serializar. [CONV] núcleo compartido para pruebas unitarias.
 */
public class NoOpReservationLockPort implements ReservationLockPort {

    private final List<ReservationId> locked = new ArrayList<>();

    @Override
    public void lock(ReservationId reservationId) {
        locked.add(reservationId);
    }

    public List<ReservationId> locked() {
        return List.copyOf(locked);
    }
}
