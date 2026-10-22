package com.seashare.seasharem3.support;

import com.seashare.seasharem3.application.port.out.ReservationInformationRepository;
import com.seashare.seasharem3.domain.exception.ReservationInfoNotFoundException;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Fake en memoria de {@link ReservationInformationRepository} con la semántica del adaptador JPA:
 * el upsert de {@code save} invalida los montos calculados y {@code saveCalculatedAmounts} exige la fila.
 * [CONV] núcleo compartido para las pruebas de UC03/UC04 y de los UC que leen la reserva.
 */
public class InMemoryReservationInformationRepository implements ReservationInformationRepository {

    private final Map<ReservationId, ReservationInformation> rows = new LinkedHashMap<>();

    @Override
    public Optional<ReservationInformation> findByReservationId(ReservationId reservationId) {
        return Optional.ofNullable(rows.get(reservationId));
    }

    @Override
    public ReservationInformation save(ReservationInformation information) {
        ReservationInformation stored = information.invalidateCalculatedAmounts();
        rows.put(stored.reservationId(), stored);
        return stored;
    }

    @Override
    public ReservationInformation saveCalculatedAmounts(ReservationInformation information) {
        if (!rows.containsKey(information.reservationId())) {
            throw new ReservationInfoNotFoundException(
                    "reservation_information not found: " + information.reservationId().value());
        }
        rows.put(information.reservationId(), information);
        return information;
    }
}
