package com.seashare.seasharem3.support;

import com.seashare.seasharem3.application.port.out.DepositDispositionRepository;
import com.seashare.seasharem3.domain.model.DepositDisposition;
import com.seashare.seasharem3.domain.valueobject.DepositDispositionStatus;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Fake en memoria de {@link DepositDispositionRepository} con el mismo CAS de un unico desenlace. */
public class InMemoryDepositDispositionRepository implements DepositDispositionRepository {

    private final Map<UUID, DepositDisposition> rows = new ConcurrentHashMap<>();

    @Override
    public Optional<DepositDisposition> findByReservationId(ReservationId reservationId) {
        return Optional.ofNullable(rows.get(reservationId.value()));
    }

    @Override
    public DepositDisposition save(DepositDisposition disposition) {
        rows.put(disposition.reservationId().value(), disposition);
        return disposition;
    }

    @Override
    public boolean compareAndSet(ReservationId reservationId, DepositDispositionStatus expected,
            DepositDisposition next) {
        DepositDisposition current = rows.get(reservationId.value());
        if (current == null || current.disposition() != expected) {
            return false;
        }
        rows.put(reservationId.value(), next);
        return true;
    }

    public void clear() {
        rows.clear();
    }
}
