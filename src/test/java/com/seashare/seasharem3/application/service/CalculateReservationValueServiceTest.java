package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.CalculateReservationValueCommand;
import com.seashare.seasharem3.application.dto.ReservationValueResult;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.out.ReservationInformationRepository;
import com.seashare.seasharem3.application.port.out.FinancialParametersRepository;
import com.seashare.seasharem3.application.port.out.ReservationLockPort;
import com.seashare.seasharem3.domain.exception.FinancialParametersNotConfiguredException;
import com.seashare.seasharem3.domain.exception.ReservationInfoIncompleteException;
import com.seashare.seasharem3.domain.exception.ReservationInfoNotFoundException;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.service.ReservationValueCalculator;
import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.domain.valueobject.ReservationValue;
import com.seashare.seasharem3.support.InMemoryFailureRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Orquestación de UC04 (T014) y registro de fallos (T018): orden E3 → E4 → E6, versión referenciada
 * frente a vigente (D-17), escritura única, bloqueo asesor antes de leer y ninguna llamada a Flota.
 * Componentes reales para el cálculo y dobles para los puertos; el registro usa
 * {@code InMemoryFailureRecorder} del núcleo compartido.
 */
@ExtendWith(MockitoExtension.class)
class CalculateReservationValueServiceTest {

    private static final UUID RESERVATION_UUID = UUID.fromString("b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10");
    private static final ReservationId RESERVATION_ID = new ReservationId(RESERVATION_UUID);
    private static final UUID FROZEN_PARAMETERS_ID = UUID.fromString("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d");
    private static final Instant CALCULATED_AT = Instant.parse("2026-11-01T10:15:30Z");
    private static final CalculateReservationValueCommand COMMAND =
            new CalculateReservationValueCommand(RESERVATION_ID);

    @Mock
    private ReservationInformationRepository reservationInformationRepository;

    @Mock
    private FinancialParametersRepository financialParametersRepository;

    @Mock
    private ReservationLockPort reservationLock;

    private InMemoryFailureRecorder failureRecorder;
    private CalculateReservationValueService service;

    @BeforeEach
    void setUp() {
        failureRecorder = new InMemoryFailureRecorder();
        service = new CalculateReservationValueService(reservationInformationRepository,
                financialParametersRepository, failureRecorder, reservationLock,
                new ReservationValueCalculator(), Clock.fixed(CALCULATED_AT, ZoneOffset.UTC));
    }

    @Test
    void calculatesAndPersistsWithTheCurrentVersionOnTheFirstCalculation() {
        when(reservationInformationRepository.findByReservationId(RESERVATION_ID))
                .thenReturn(Optional.of(information(null)));
        when(financialParametersRepository.findCurrent()).thenReturn(Optional.of(parameters()));

        ReservationValueResult result = service.calculate(COMMAND);

        assertThat(result.reservationId()).isEqualTo(RESERVATION_ID);
        assertThat(result.rentalAmount().amount()).isEqualByComparingTo("900000.00");
        assertThat(result.insuranceAmount().amount()).isEqualByComparingTo("60000.00");
        assertThat(result.guaranteeDepositAmount().amount()).isEqualByComparingTo("30000.00");
        assertThat(result.totalAmount().amount()).isEqualByComparingTo("990000.00");
        assertThat(savedInformation()).satisfies(saved -> {
            assertThat(saved.isCalculated()).isTrue();
            assertThat(saved.frozenParametersId()).isEqualTo(FROZEN_PARAMETERS_ID);
            assertThat(saved.calculatedAt()).isEqualTo(CALCULATED_AT);
            assertThat(saved.totalAmount().amount()).isEqualByComparingTo("990000.00");
        });
        assertThat(failureRecorder.failures()).isEmpty();
    }

    @Test
    void locksTheReservationBeforeReadingIt() {
        when(reservationInformationRepository.findByReservationId(RESERVATION_ID))
                .thenReturn(Optional.of(information(null)));
        when(financialParametersRepository.findCurrent()).thenReturn(Optional.of(parameters()));

        service.calculate(COMMAND);

        InOrder order = inOrder(reservationLock, reservationInformationRepository);
        order.verify(reservationLock).lock(RESERVATION_ID);
        order.verify(reservationInformationRepository).findByReservationId(RESERVATION_ID);
    }

    @Test
    void resolvesTheReferencedVersionOnRecalculationSoTheResultStaysDeterministic() {
        when(reservationInformationRepository.findByReservationId(RESERVATION_ID))
                .thenReturn(Optional.of(information(FROZEN_PARAMETERS_ID)));
        when(financialParametersRepository.findById(FROZEN_PARAMETERS_ID)).thenReturn(Optional.of(parameters()));

        service.calculate(COMMAND);

        verify(financialParametersRepository).findById(FROZEN_PARAMETERS_ID);
        verify(financialParametersRepository, never()).findCurrent();
    }

    @Test
    void answers404WithoutRecordingOrWritingWhenTheReservationIsNotRegisteredYet() {
        when(reservationInformationRepository.findByReservationId(RESERVATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calculate(COMMAND))
                .isInstanceOfSatisfying(ReservationInfoNotFoundException.class,
                        exception -> assertThat(exception.getMessage()).contains(RESERVATION_UUID.toString()));

        verifyNoInteractions(financialParametersRepository);
        verify(reservationInformationRepository, never()).saveCalculatedAmounts(any());
        assertThat(failureRecorder.failures()).isEmpty();
    }

    @Test
    void recordsAndAnswers503WhenThereIsNoVersionOfTheParameters() {
        when(reservationInformationRepository.findByReservationId(RESERVATION_ID))
                .thenReturn(Optional.of(information(null)));
        when(financialParametersRepository.findCurrent()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calculate(COMMAND))
                .isInstanceOf(FinancialParametersNotConfiguredException.class);

        assertFailureRecorded(ErrorCode.FINANCIAL_PARAMETERS_NOT_CONFIGURED);
        verify(reservationInformationRepository, never()).saveCalculatedAmounts(any());
    }

    @Test
    void recordsAndAnswers503WhenTheVersionLacksTheInsuranceFee() {
        FinancialParameters withoutInsurance = mock(FinancialParameters.class);
        when(withoutInsurance.insuranceFeePerPassenger()).thenReturn(null);
        when(reservationInformationRepository.findByReservationId(RESERVATION_ID))
                .thenReturn(Optional.of(information(null)));
        when(financialParametersRepository.findCurrent()).thenReturn(Optional.of(withoutInsurance));

        assertThatThrownBy(() -> service.calculate(COMMAND))
                .isInstanceOf(FinancialParametersNotConfiguredException.class);

        assertFailureRecorded(ErrorCode.FINANCIAL_PARAMETERS_NOT_CONFIGURED);
        verify(reservationInformationRepository, never()).saveCalculatedAmounts(any());
    }

    /**
     * Vía defensiva: {@code FinancialParameters} exige la comisión no nula y la columna es
     * {@code NOT NULL}, por lo que el caso solo es construible con un doble (decisión D-UC04-02).
     */
    @Test
    void recordsAndAnswers503WhenTheVersionLacksTheCommissionPercentage() {
        FinancialParameters withoutCommission = mock(FinancialParameters.class);
        when(withoutCommission.insuranceFeePerPassenger()).thenReturn(new Money(new BigDecimal("15000.00")));
        when(reservationInformationRepository.findByReservationId(RESERVATION_ID))
                .thenReturn(Optional.of(information(null)));
        when(financialParametersRepository.findCurrent()).thenReturn(Optional.of(withoutCommission));

        assertThatThrownBy(() -> service.calculate(COMMAND))
                .isInstanceOf(FinancialParametersNotConfiguredException.class);

        assertFailureRecorded(ErrorCode.FINANCIAL_PARAMETERS_NOT_CONFIGURED);
        verify(reservationInformationRepository, never()).saveCalculatedAmounts(any());
    }

    /**
     * Vía defensiva de integridad (D-UC04-02): {@code base_rate} es {@code NOT NULL} en la migración
     * inicial, por lo que el 422 solo es construible con un doble; nunca se calcula en parte.
     */
    @Test
    void recordsAndAnswers422WhenTheRegisteredInformationIsNotCalculable() {
        ReservationInformation incomplete = mock(ReservationInformation.class);
        when(incomplete.frozenParametersId()).thenReturn(null);
        when(incomplete.baseRate()).thenReturn(null);
        when(reservationInformationRepository.findByReservationId(RESERVATION_ID))
                .thenReturn(Optional.of(incomplete));
        when(financialParametersRepository.findCurrent()).thenReturn(Optional.of(parameters()));

        assertThatThrownBy(() -> service.calculate(COMMAND))
                .isInstanceOf(ReservationInfoIncompleteException.class);

        assertFailureRecorded(ErrorCode.RESERVATION_INFO_INCOMPLETE);
        verify(reservationInformationRepository, never()).saveCalculatedAmounts(any());
    }

    /** Orden de validación: E3 → E4 → E6 (contracts/README §4.3); E2 se prueba en el contrato web. */
    @Test
    void evaluatesTheParametersBeforeTheCompletenessOfTheRegisteredInformation() {
        ReservationInformation incomplete = mock(ReservationInformation.class);
        when(incomplete.frozenParametersId()).thenReturn(null);
        when(reservationInformationRepository.findByReservationId(RESERVATION_ID))
                .thenReturn(Optional.of(incomplete));
        when(financialParametersRepository.findCurrent()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calculate(COMMAND))
                .isInstanceOf(FinancialParametersNotConfiguredException.class);

        verify(incomplete, never()).baseRate();
        assertFailureRecorded(ErrorCode.FINANCIAL_PARAMETERS_NOT_CONFIGURED);
    }

    private ReservationInformation savedInformation() {
        ArgumentCaptor<ReservationInformation> saved = ArgumentCaptor.forClass(ReservationInformation.class);
        verify(reservationInformationRepository).saveCalculatedAmounts(saved.capture());
        return saved.getValue();
    }

    private void assertFailureRecorded(ErrorCode code) {
        assertThat(failureRecorder.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.useCase()).isEqualTo("UC04");
            assertThat(failure.reservationId()).isEqualTo(RESERVATION_UUID);
            assertThat(failure.reason()).isEqualTo(code.name());
            assertThat(failure.payloadRef()).isEqualTo("reservationId=" + RESERVATION_UUID);
        });
    }

    private static ReservationInformation information(UUID frozenParametersId) {
        ReservationInformation information = ReservationInformation.create(
                RESERVATION_ID,
                new BoatId(UUID.fromString("3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01")),
                new BaseRate(new Money(new BigDecimal("300000.00"))),
                null,
                LocalDate.of(2026, 12, 10),
                LocalDate.of(2026, 12, 12),
                4,
                new OwnerId(UUID.fromString("a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d")),
                60);
        if (frozenParametersId == null) {
            return information;
        }
        return information.withCalculated(
                new ReservationValue(
                        new Money(new BigDecimal("900000.00")), new Money(new BigDecimal("60000.00")),
                        new Money(new BigDecimal("30000.00")), new Money(new BigDecimal("990000.00"))),
                frozenParametersId, CALCULATED_AT);
    }

    private static FinancialParameters parameters() {
        return FinancialParameters.create(FROZEN_PARAMETERS_ID, 1, new Percentage(new BigDecimal("20.00")),
                new Money(new BigDecimal("15000.00")), Percentage.ZERO, Percentage.ZERO,
                "admin@seashare.com", Instant.parse("2026-10-01T12:00:00Z"));
    }
}
