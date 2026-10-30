package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.PaymentConfirmationQuery;
import com.seashare.seasharem3.application.dto.PaymentConfirmationResult;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.domain.exception.ChargeIntentNotFoundException;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.ChargeIntentStatus;
import com.seashare.seasharem3.domain.valueobject.PaymentConfirmationStatus;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.support.ChargeFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas de la consulta de confirmación de pago: intención presente/ausente, estados, montos
 * disponibles y solo lectura. Fuente: UC06 plan T004; SPEC 06 RF-002…RF-005.
 */
@ExtendWith(MockitoExtension.class)
class GetPaymentConfirmationServiceTest {

    private static final ReservationInformation RESERVATION = ChargeFixtures.calculatedReservation();
    private static final ReservationId RESERVATION_ID = RESERVATION.reservationId();

    @Mock
    private ChargeIntentRepository chargeIntentRepository;

    private GetPaymentConfirmationService service;

    @BeforeEach
    void setUp() {
        service = new GetPaymentConfirmationService(chargeIntentRepository);
    }

    @Test
    void returnsApprovedConfirmationWithTheChargedAmountAndAvailableDetail() {
        ChargeIntent intent = ChargeFixtures.intentWithStatus(RESERVATION, ChargeIntentStatus.CAPTURED,
                "pg-8841", "accredited", ChargeFixtures.CREATED_AT);
        when(chargeIntentRepository.findLatestByReservationId(RESERVATION_ID)).thenReturn(Optional.of(intent));

        PaymentConfirmationResult result = service.get(new PaymentConfirmationQuery(RESERVATION_ID));

        assertThat(result).isEqualTo(new PaymentConfirmationResult(RESERVATION_ID,
                PaymentConfirmationStatus.APPROVED, "accredited", null, null, null,
                new BigDecimal("990000.00"), "pg-8841"));
    }

    @Test
    void returnsRejectedWithoutAChargedAmount() {
        ChargeIntent intent = ChargeFixtures.intentWithStatus(RESERVATION, ChargeIntentStatus.REJECTED,
                "pg-1", "cc_rejected", ChargeFixtures.CREATED_AT);
        when(chargeIntentRepository.findLatestByReservationId(RESERVATION_ID)).thenReturn(Optional.of(intent));

        PaymentConfirmationResult result = service.get(new PaymentConfirmationQuery(RESERVATION_ID));

        assertThat(result.status()).isEqualTo(PaymentConfirmationStatus.REJECTED);
        assertThat(result.detail()).isEqualTo("cc_rejected");
        assertThat(result.chargedAmount()).isNull();
        assertThat(result.authorizedAmount()).isNull();
        assertThat(result.capturedAmount()).isNull();
        assertThat(result.releasedAmount()).isNull();
        assertThat(result.externalReference()).isEqualTo("pg-1");
    }

    @Test
    void mapsPendingSendToInProcessWithoutAmounts() {
        ChargeIntent intent = ChargeFixtures.intentWithStatus(RESERVATION, ChargeIntentStatus.PENDING_SEND,
                null, null, ChargeFixtures.CREATED_AT);
        when(chargeIntentRepository.findLatestByReservationId(RESERVATION_ID)).thenReturn(Optional.of(intent));

        PaymentConfirmationResult result = service.get(new PaymentConfirmationQuery(RESERVATION_ID));

        assertThat(result.status()).isEqualTo(PaymentConfirmationStatus.IN_PROCESS);
        assertThat(result.chargedAmount()).isNull();
        assertThat(result.detail()).isNull();
        assertThat(result.externalReference()).isNull();
    }

    @Test
    void mapsCommunicationErrorToUnknownWithoutAmounts() {
        ChargeIntent intent = ChargeFixtures.intentWithStatus(RESERVATION, ChargeIntentStatus.COMMUNICATION_ERROR,
                null, "gateway timeout", ChargeFixtures.CREATED_AT);
        when(chargeIntentRepository.findLatestByReservationId(RESERVATION_ID)).thenReturn(Optional.of(intent));

        PaymentConfirmationResult result = service.get(new PaymentConfirmationQuery(RESERVATION_ID));

        assertThat(result.status()).isEqualTo(PaymentConfirmationStatus.UNKNOWN);
        assertThat(result.detail()).isEqualTo("gateway timeout");
        assertThat(result.chargedAmount()).isNull();
    }

    @Test
    void throwsNotFoundWhenThereIsNoIntentForTheReservation() {
        when(chargeIntentRepository.findLatestByReservationId(RESERVATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(new PaymentConfirmationQuery(RESERVATION_ID)))
                .isInstanceOf(ChargeIntentNotFoundException.class)
                .hasMessage("There is no ChargeIntent for reservation " + RESERVATION_ID.value() + ".");
    }

    @Test
    void readsOnlyTheLatestIntentAndNeverWrites() {
        ChargeIntent intent = ChargeFixtures.intentWithStatus(RESERVATION, ChargeIntentStatus.EXPIRED,
                null, null, ChargeFixtures.CREATED_AT);
        when(chargeIntentRepository.findLatestByReservationId(RESERVATION_ID)).thenReturn(Optional.of(intent));

        service.get(new PaymentConfirmationQuery(RESERVATION_ID));

        verify(chargeIntentRepository).findLatestByReservationId(RESERVATION_ID);
        verifyNoMoreInteractions(chargeIntentRepository);
    }

    @Test
    void rejectsANullQuery() {
        assertThatThrownBy(() -> service.get(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("query");
    }
}
