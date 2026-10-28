package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.BaseRateResult;
import com.seashare.seasharem3.application.dto.ProvideBaseRateCommand;
import com.seashare.seasharem3.application.dto.RegisterReservationInformationCommand;
import com.seashare.seasharem3.application.port.in.ProvideBaseRateUseCase;
import com.seashare.seasharem3.application.port.in.RegisterReservationInformationUseCase;
import com.seashare.seasharem3.application.port.out.ReservationInformationRepository;
import com.seashare.seasharem3.domain.exception.BaseRateUnavailableException;
import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.exception.ReservationInfoIncompleteException;
import com.seashare.seasharem3.domain.exception.ReservationInformationValidationException;
import com.seashare.seasharem3.domain.model.ReservationInformation;

import java.util.List;
import java.util.Objects;

/**
 * Orquesta UC03: valida la solicitud, incluye UC02 para obtener la tarifa vigente y persiste el
 * <em>upsert</em> de {@code reservation_information}.
 *
 * <p>No registra fallos: el listener clasifica los errores permanentes (registro + {@code ack}) y
 * deja propagar los transitorios para la política de reintentos/DLQ compartida. La validación ocurre
 * antes de invocar a UC02 para no gastar una consulta externa en un mensaje ya inválido. Fuente:
 * UC03 plan T017, reglas 3, 5 y 15; RF-002, RF-005 y RF-007.</p>
 */
public class RegisterReservationInformationService implements RegisterReservationInformationUseCase {

    private final ProvideBaseRateUseCase provideBaseRateUseCase;
    private final ReservationInformationRepository reservationInformationRepository;

    public RegisterReservationInformationService(ProvideBaseRateUseCase provideBaseRateUseCase,
            ReservationInformationRepository reservationInformationRepository) {
        this.provideBaseRateUseCase = Objects.requireNonNull(provideBaseRateUseCase, "provideBaseRateUseCase");
        this.reservationInformationRepository = Objects.requireNonNull(reservationInformationRepository,
                "reservationInformationRepository");
    }

    @Override
    public void register(RegisterReservationInformationCommand command) {
        validate(command);
        BaseRateResult baseRate = provideBaseRate(command);
        reservationInformationRepository.save(createInformation(command, baseRate));
    }

    private static void validate(RegisterReservationInformationCommand command) {
        if (command == null) {
            throw new ReservationInformationValidationException("command is required");
        }
        if (command.reservationId() == null) {
            throw new ReservationInformationValidationException("reservation_id is required");
        }
        if (command.boatId() == null) {
            throw new ReservationInformationValidationException("boat_id is required");
        }
        if (command.startDate() == null) {
            throw new ReservationInformationValidationException("start_date is required");
        }
        if (command.endDate() == null) {
            throw new ReservationInformationValidationException("end_date is required");
        }
        if (command.endDate().isBefore(command.startDate())) {
            throw new ReservationInformationValidationException(
                    "end_date cannot be before start_date: " + command.startDate() + " - " + command.endDate());
        }
        if (command.passengers() < 1) {
            throw new ReservationInformationValidationException(
                    "passengers must be at least 1: " + command.passengers());
        }
        if (command.maxCapacity() < 1) {
            throw new ReservationInformationValidationException(
                    "max_capacity must be at least 1: " + command.maxCapacity());
        }
        if (command.passengers() > command.maxCapacity()) {
            throw new ReservationInformationValidationException(
                    "passengers must be between 1 and max_capacity: " + command.passengers()
                            + " > " + command.maxCapacity());
        }
        if (command.ownerId() == null) {
            throw new ReservationInfoIncompleteException("owner_id is required");
        }
    }

    private BaseRateResult provideBaseRate(RegisterReservationInformationCommand command) {
        List<BaseRateResult> results = provideBaseRateUseCase.provideBaseRates(
                new ProvideBaseRateCommand(List.of(command.boatId()), command.startDate()));
        if (results == null || results.isEmpty()) {
            throw new BaseRateUnavailableException(
                    "No base rate available for boat " + command.boatId().value());
        }
        return results.stream()
                .filter(result -> result != null && command.boatId().equals(result.boatId()))
                .filter(result -> result.baseRate() != null)
                .findFirst()
                .orElseThrow(() -> new BaseRateUnavailableException(
                        "No base rate available for boat " + command.boatId().value()));
    }

    private static ReservationInformation createInformation(RegisterReservationInformationCommand command,
            BaseRateResult baseRate) {
        try {
            return ReservationInformation.create(
                    command.reservationId(),
                    command.boatId(),
                    baseRate.baseRate(),
                    baseRate.parametersId(),
                    command.startDate(),
                    command.endDate(),
                    command.passengers(),
                    command.ownerId(),
                    command.maxCapacity());
        } catch (DomainException exception) {
            throw new ReservationInformationValidationException(exception.getMessage(), exception);
        }
    }
}
