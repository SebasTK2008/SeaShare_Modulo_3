package com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto;

import com.seashare.seasharem3.application.dto.RegisterReservationInformationCommand;
import com.seashare.seasharem3.domain.exception.ReservationInfoIncompleteException;
import com.seashare.seasharem3.domain.exception.ReservationInformationValidationException;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Traduce el mensaje AMQP al comando de aplicación de UC03.
 *
 * <p>Valida la presencia de los campos obligatorios antes de construir los VOs: los datos de la
 * reserva que faltan son un mensaje inválido (E2), mientras que la ausencia de {@code owner_id} o
 * {@code max_capacity} es información incompleta (E6) responsabilidad de Reservas. Fuente: UC03 plan
 * T007, RF-001, RNF-001 y reglas 1 y 4.</p>
 */
@Component
public class ReservationInfoMessageMapper {

    public RegisterReservationInformationCommand toCommand(ReservationInfoMessage message) {
        Objects.requireNonNull(message, "message");

        if (message.reservationId() == null) {
            throw new ReservationInformationValidationException("reservation_id is required");
        }
        if (message.boatId() == null) {
            throw new ReservationInformationValidationException("boat_id is required");
        }
        if (message.startDate() == null) {
            throw new ReservationInformationValidationException("start_date is required");
        }
        if (message.endDate() == null) {
            throw new ReservationInformationValidationException("end_date is required");
        }
        if (message.passengers() == null) {
            throw new ReservationInformationValidationException("passengers is required");
        }
        if (message.ownerId() == null) {
            throw new ReservationInfoIncompleteException("owner_id is required");
        }
        if (message.maxCapacity() == null) {
            throw new ReservationInfoIncompleteException("max_capacity is required");
        }

        return new RegisterReservationInformationCommand(
                new ReservationId(message.reservationId()),
                new BoatId(message.boatId()),
                message.startDate(),
                message.endDate(),
                message.passengers(),
                new OwnerId(message.ownerId()),
                message.maxCapacity());
    }
}
