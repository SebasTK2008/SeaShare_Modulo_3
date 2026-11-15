package com.seashare.seasharem3.acceptance;

import com.seashare.seasharem3.application.dto.PaymentConfirmationQuery;
import com.seashare.seasharem3.application.dto.PaymentConfirmationResult;
import com.seashare.seasharem3.application.port.in.GetPaymentConfirmationUseCase;
import com.seashare.seasharem3.application.port.out.ChargeGatewayPort;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.application.service.GetPaymentConfirmationService;
import com.seashare.seasharem3.domain.exception.ChargeIntentNotFoundException;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.ChargeIntentStatus;
import com.seashare.seasharem3.domain.valueobject.PaymentConfirmationStatus;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.infrastructure.adapter.in.web.PaymentConfirmationController;
import com.seashare.seasharem3.support.ChargeFixtures;
import com.seashare.seasharem3.support.InMemoryChargeIntentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de aceptación de UC06 sobre la consulta real (sin mocks): estado devuelto, cero contacto
 * con la pasarela y error controlado sin intención. Fuente: UC06 plan T011–T013; SPEC 06 CE-001…
 * CE-003.
 */
class Uc06AcceptanceTest {

    private static final ReservationInformation RESERVATION = ChargeFixtures.calculatedReservation();
    private static final ReservationId RESERVATION_ID = RESERVATION.reservationId();

    private final InMemoryChargeIntentRepository intents = new InMemoryChargeIntentRepository();
    private final GetPaymentConfirmationUseCase service = new GetPaymentConfirmationService(intents,
            reservationId -> java.util.Optional.empty());

    @ParameterizedTest
    @CsvSource({
            "PENDING_SEND, IN_PROCESS",
            "IN_PROCESS, IN_PROCESS",
            "CAPTURED, APPROVED",
            "REJECTED, REJECTED",
            "CANCELLED, CANCELLED",
            "EXPIRED, EXPIRED",
            "COMMUNICATION_ERROR, UNKNOWN"
    })
    void ce001_estado_devuelto_coincide_con_la_intencion(ChargeIntentStatus internal,
            PaymentConfirmationStatus expected) {
        intents.save(ChargeFixtures.intentWithStatus(RESERVATION, internal, "pg-1", "detail",
                ChargeFixtures.CREATED_AT));

        PaymentConfirmationResult result = service.get(new PaymentConfirmationQuery(RESERVATION_ID));

        assertThat(result.status()).isEqualTo(expected);
    }

    @Test
    void ce001_approvedReportsOnlyTheChargedAmount() {
        intents.save(ChargeFixtures.intentWithStatus(RESERVATION, ChargeIntentStatus.CAPTURED,
                "pg-8841", "accredited", ChargeFixtures.CREATED_AT));

        PaymentConfirmationResult result = service.get(new PaymentConfirmationQuery(RESERVATION_ID));

        assertThat(result.status()).isEqualTo(PaymentConfirmationStatus.APPROVED);
        assertThat(result.chargedAmount()).isEqualTo(new BigDecimal("990000.00"));
        assertThat(result.authorizedAmount()).isNull();
        assertThat(result.capturedAmount()).isNull();
        assertThat(result.releasedAmount()).isNull();
        assertThat(result.detail()).isEqualTo("accredited");
        assertThat(result.externalReference()).isEqualTo("pg-8841");
    }

    @Test
    void ce002_no_se_realizan_solicitudes_a_la_pasarela() {
        assertThat(Arrays.stream(GetPaymentConfirmationService.class.getDeclaredFields()))
                .noneMatch(field -> ChargeGatewayPort.class.isAssignableFrom(field.getType()));

        for (Constructor<?> constructor : GetPaymentConfirmationService.class.getDeclaredConstructors()) {
            assertThat(constructor.getParameterTypes()).containsExactly(ChargeIntentRepository.class,
                    com.seashare.seasharem3.application.port.out.RefundRecordQueryPort.class);
        }
        for (Constructor<?> constructor : PaymentConfirmationController.class.getDeclaredConstructors()) {
            assertThat(constructor.getParameterTypes()).containsExactly(GetPaymentConfirmationUseCase.class);
        }
    }

    @Test
    void ce003_reserva_sin_intencion_responde_error_controlado() {
        assertThatThrownBy(() -> service.get(new PaymentConfirmationQuery(RESERVATION_ID)))
                .isInstanceOf(ChargeIntentNotFoundException.class)
                .hasMessage("There is no ChargeIntent for reservation " + RESERVATION_ID.value() + ".");
    }

    @Test
    void multipleConsultationsReturnTheCurrentStateWithoutCreatingRecords() {
        ChargeIntent intent = ChargeFixtures.intentWithStatus(RESERVATION, ChargeIntentStatus.REJECTED,
                "pg-1", "cc_rejected", ChargeFixtures.CREATED_AT);
        intents.save(intent);

        PaymentConfirmationResult first = service.get(new PaymentConfirmationQuery(RESERVATION_ID));
        PaymentConfirmationResult second = service.get(new PaymentConfirmationQuery(RESERVATION_ID));

        assertThat(first).isEqualTo(second);
        assertThat(intents.findByStatusIn(Arrays.asList(ChargeIntentStatus.values()))).hasSize(1);
    }
}
