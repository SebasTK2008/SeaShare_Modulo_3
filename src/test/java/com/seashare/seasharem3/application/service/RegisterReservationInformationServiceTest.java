package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.BaseRateResult;
import com.seashare.seasharem3.application.dto.ProvideBaseRateCommand;
import com.seashare.seasharem3.application.dto.RegisterReservationInformationCommand;
import com.seashare.seasharem3.application.port.in.ProvideBaseRateUseCase;
import com.seashare.seasharem3.application.port.out.ReservationInformationRepository;
import com.seashare.seasharem3.domain.exception.BaseRateException;
import com.seashare.seasharem3.domain.exception.BaseRateException.BaseRateFailureReason;
import com.seashare.seasharem3.domain.exception.BaseRateUnavailableException;
import com.seashare.seasharem3.domain.exception.ReservationInfoIncompleteException;
import com.seashare.seasharem3.domain.exception.ReservationInformationValidationException;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de la orquestación de UC03: validación previa a UC02, inclusión de UC02 con
 * {@code boat_id} y {@code start_date}, correlación por embarcación, tarifa no disponible y
 * propagación de fallas transitorias. No calcula montos (RF-007).
 */
class RegisterReservationInformationServiceTest {

    private static final ReservationId RESERVATION_ID = new ReservationId(
            UUID.fromString("b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10"));
    private static final BoatId BOAT = new BoatId(
            UUID.fromString("3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01"));
    private static final BoatId OTHER_BOAT = new BoatId(
            UUID.fromString("4d7a9c10-5a2b-4e6f-8d01-3f2c1a548b3e"));
    private static final OwnerId OWNER = new OwnerId(
            UUID.fromString("a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d"));
    private static final LocalDate START = LocalDate.of(2026, 12, 20);
    private static final LocalDate END = LocalDate.of(2026, 12, 22);
    private static final BaseRate BASE_RATE = new BaseRate(new Money(new BigDecimal("437500.0000")));
    private static final UUID PARAMETERS_ID = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final RegisterReservationInformationCommand COMMAND = new RegisterReservationInformationCommand(
            RESERVATION_ID, BOAT, START, END, 4, OWNER, 6);

    private ProvideBaseRateUseCase provideBaseRateUseCase;
    private ReservationInformationRepository repository;
    private RegisterReservationInformationService service;

    @BeforeEach
    void setUp() {
        provideBaseRateUseCase = mock(ProvideBaseRateUseCase.class);
        repository = mock(ReservationInformationRepository.class);
        service = new RegisterReservationInformationService(provideBaseRateUseCase, repository);
    }

    @Test
    void registersTheReservationWithTheBaseRateFromUc02() {
        when(provideBaseRateUseCase.provideBaseRates(any()))
                .thenReturn(List.of(new BaseRateResult(BOAT, BASE_RATE, PARAMETERS_ID)));

        service.register(COMMAND);

        ArgumentCaptor<ProvideBaseRateCommand> rateCommand = ArgumentCaptor.forClass(ProvideBaseRateCommand.class);
        verify(provideBaseRateUseCase, times(1)).provideBaseRates(rateCommand.capture());
        assertThat(rateCommand.getValue().boatIds()).containsExactly(BOAT);
        assertThat(rateCommand.getValue().evaluatedDate()).isEqualTo(START);
        assertThat(rateCommand.getValue().parameters()).isNull();

        ReservationInformation saved = captureSaved();
        assertThat(saved.reservationId()).isEqualTo(RESERVATION_ID);
        assertThat(saved.boatId()).isEqualTo(BOAT);
        assertThat(saved.startDate()).isEqualTo(START);
        assertThat(saved.endDate()).isEqualTo(END);
        assertThat(saved.passengers()).isEqualTo(4);
        assertThat(saved.ownerId()).isEqualTo(OWNER);
        assertThat(saved.maxCapacity()).isEqualTo(6);
        assertThat(saved.baseRate()).isEqualTo(BASE_RATE);
        assertThat(saved.baseRate().amount().amount()).isEqualByComparingTo("437500.0000");
        assertThat(saved.baseRate().amount().amount().scale()).isEqualTo(4);
        assertThat(saved.baseRateParametersId()).isEqualTo(PARAMETERS_ID);
        assertThat(saved.isCalculated()).isFalse();
        assertThat(saved.rentalAmount()).isNull();
        assertThat(saved.insuranceAmount()).isNull();
        assertThat(saved.depositAmount()).isNull();
        assertThat(saved.totalAmount()).isNull();
    }

    @Test
    void reRegistersAnExistingReservationAndConsultsUc02Again() {
        when(provideBaseRateUseCase.provideBaseRates(any()))
                .thenReturn(List.of(new BaseRateResult(BOAT, BASE_RATE, null)));

        service.register(COMMAND);
        service.register(COMMAND);

        verify(provideBaseRateUseCase, times(2)).provideBaseRates(any());
        verify(repository, times(2)).save(any(ReservationInformation.class));
    }

    @Test
    void persistsNullParametersIdWhenTheDateIsRegular() {
        when(provideBaseRateUseCase.provideBaseRates(any()))
                .thenReturn(List.of(new BaseRateResult(BOAT, BASE_RATE, null)));

        service.register(COMMAND);

        assertThat(captureSaved().baseRateParametersId()).isNull();
    }

    @Test
    void rejectsAnInvertedDateRangeBeforeCallingUc02() {
        RegisterReservationInformationCommand command = new RegisterReservationInformationCommand(
                RESERVATION_ID, BOAT, END, START, 4, OWNER, 6);

        assertRejected(command, ReservationInformationValidationException.class);
    }

    @Test
    void rejectsPassengersAboveCapacityBeforeCallingUc02() {
        RegisterReservationInformationCommand command = new RegisterReservationInformationCommand(
                RESERVATION_ID, BOAT, START, END, 7, OWNER, 6);

        assertRejected(command, ReservationInformationValidationException.class);
    }

    @Test
    void rejectsNonPositivePassengersBeforeCallingUc02() {
        RegisterReservationInformationCommand command = new RegisterReservationInformationCommand(
                RESERVATION_ID, BOAT, START, END, 0, OWNER, 6);

        assertRejected(command, ReservationInformationValidationException.class);
    }

    @Test
    void rejectsNonPositiveCapacityBeforeCallingUc02() {
        RegisterReservationInformationCommand command = new RegisterReservationInformationCommand(
                RESERVATION_ID, BOAT, START, END, 1, OWNER, 0);

        assertRejected(command, ReservationInformationValidationException.class);
    }

    @Test
    void rejectsMissingRequiredDatesBeforeCallingUc02() {
        assertRejected(new RegisterReservationInformationCommand(
                RESERVATION_ID, BOAT, null, END, 4, OWNER, 6), ReservationInformationValidationException.class);
        assertRejected(new RegisterReservationInformationCommand(
                RESERVATION_ID, BOAT, START, null, 4, OWNER, 6), ReservationInformationValidationException.class);
    }

    @Test
    void rejectsMissingIdentifiersBeforeCallingUc02() {
        assertRejected(new RegisterReservationInformationCommand(
                null, BOAT, START, END, 4, OWNER, 6), ReservationInformationValidationException.class);
        assertRejected(new RegisterReservationInformationCommand(
                RESERVATION_ID, null, START, END, 4, OWNER, 6), ReservationInformationValidationException.class);
    }

    @Test
    void rejectsMissingOwnerAsIncompleteInformationBeforeCallingUc02() {
        assertRejected(new RegisterReservationInformationCommand(
                RESERVATION_ID, BOAT, START, END, 4, null, 6), ReservationInfoIncompleteException.class);
    }

    @Test
    void rejectsNullCommand() {
        assertThatThrownBy(() -> service.register(null))
                .isInstanceOf(ReservationInformationValidationException.class);
        verifyNoInteractions(provideBaseRateUseCase, repository);
    }

    @Test
    void throwsBaseRateUnavailableWhenUc02ReturnsNoResult() {
        when(provideBaseRateUseCase.provideBaseRates(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.register(COMMAND))
                .isInstanceOf(BaseRateUnavailableException.class)
                .hasMessageContaining(BOAT.value().toString());
        verify(repository, never()).save(any());
    }

    @Test
    void throwsBaseRateUnavailableWhenUc02ReturnsNull() {
        when(provideBaseRateUseCase.provideBaseRates(any())).thenReturn(null);

        assertThatThrownBy(() -> service.register(COMMAND))
                .isInstanceOf(BaseRateUnavailableException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void throwsBaseRateUnavailableWhenUc02ReturnsAnotherBoat() {
        when(provideBaseRateUseCase.provideBaseRates(any()))
                .thenReturn(List.of(new BaseRateResult(OTHER_BOAT, BASE_RATE, null)));

        assertThatThrownBy(() -> service.register(COMMAND))
                .isInstanceOf(BaseRateUnavailableException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void throwsBaseRateUnavailableWhenTheResultHasNoBaseRate() {
        when(provideBaseRateUseCase.provideBaseRates(any()))
                .thenReturn(List.of(new BaseRateResult(BOAT, null, null)));

        assertThatThrownBy(() -> service.register(COMMAND))
                .isInstanceOf(BaseRateUnavailableException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void propagatesTransientBaseRateFailures() {
        when(provideBaseRateUseCase.provideBaseRates(any()))
                .thenThrow(new BaseRateException(BaseRateFailureReason.FLEET_UNAVAILABLE, "fleet down"));

        assertThatThrownBy(() -> service.register(COMMAND))
                .isInstanceOfSatisfying(BaseRateException.class,
                        exception -> assertThat(exception.isTransient()).isTrue());
        verify(repository, never()).save(any());
    }

    @Test
    void propagatesPermanentBaseRateFailures() {
        when(provideBaseRateUseCase.provideBaseRates(any()))
                .thenThrow(new BaseRateException(BaseRateFailureReason.BASE_RATE_NOT_AVAILABLE, "no rate"));

        assertThatThrownBy(() -> service.register(COMMAND))
                .isInstanceOfSatisfying(BaseRateException.class,
                        exception -> assertThat(exception.isTransient()).isFalse());
        verify(repository, never()).save(any());
    }

    private void assertRejected(RegisterReservationInformationCommand command,
            Class<? extends RuntimeException> expectedType) {
        assertThatThrownBy(() -> service.register(command)).isInstanceOf(expectedType);
        verifyNoInteractions(provideBaseRateUseCase, repository);
    }

    private ReservationInformation captureSaved() {
        ArgumentCaptor<ReservationInformation> information = ArgumentCaptor.forClass(ReservationInformation.class);
        verify(repository).save(information.capture());
        return information.getValue();
    }
}
