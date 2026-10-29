package com.seashare.seasharem3.acceptance;

import com.seashare.seasharem3.application.dto.ChargeCommand;
import com.seashare.seasharem3.application.dto.ChargeGatewayResult;
import com.seashare.seasharem3.application.dto.GatewayWebhookNotification;
import com.seashare.seasharem3.application.dto.ProcessChargeCommand;
import com.seashare.seasharem3.application.port.in.ProcessChargeUseCase;
import com.seashare.seasharem3.application.port.in.SendChargeToGatewayUseCase;
import com.seashare.seasharem3.application.service.ChargePaymentWebhookHandler;
import com.seashare.seasharem3.application.service.ProcessChargeService;
import com.seashare.seasharem3.application.service.RegisterChargeResultService;
import com.seashare.seasharem3.application.service.SendChargeToGatewayService;
import com.seashare.seasharem3.domain.exception.GatewayCommunicationException;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.model.ChargeRecord;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.ChargeIntentStatus;
import com.seashare.seasharem3.support.ChargeFixtures;
import com.seashare.seasharem3.support.InMemoryChargeIntentRepository;
import com.seashare.seasharem3.support.InMemoryChargeRecordRepository;
import com.seashare.seasharem3.support.InMemoryFailureRecorder;
import com.seashare.seasharem3.support.InMemoryReservationInformationRepository;
import com.seashare.seasharem3.support.RecordingChargeCommandPublisher;
import com.seashare.seasharem3.support.StubChargeGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Pruebas de aceptacion de UC05 (CE-001 a CE-007) sobre el flujo completo: UC07 dispara el cobro,
 * el worker lo envia a la pasarela, la pasarela responde y el resultado se registra; el webhook
 * reporta el resultado definitivo.
 *
 * <p>Fuente: UC05 plan T016, T017, T025 a T029; SPEC 5 Criterios de Exito.</p>
 */
class Uc05AcceptanceTest {

    private static final String PAYER_EMAIL = "arrendatario@example.com";
    private static final String EXTERNAL_REFERENCE = "987654";

    private final InMemoryReservationInformationRepository reservations = new InMemoryReservationInformationRepository();
    private final InMemoryChargeIntentRepository intents = new InMemoryChargeIntentRepository();
    private final InMemoryChargeRecordRepository records = new InMemoryChargeRecordRepository();
    private final RecordingChargeCommandPublisher publisher = new RecordingChargeCommandPublisher();
    private final StubChargeGateway gateway = new StubChargeGateway();
    private final InMemoryFailureRecorder failures = new InMemoryFailureRecorder();

    private ProcessChargeUseCase processCharge;
    private SendChargeToGatewayUseCase sendCharge;
    private ChargePaymentWebhookHandler webhookHandler;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.systemUTC();
        RegisterChargeResultService registerResult = new RegisterChargeResultService(intents, records, records,
                reservations, failures, clock);
        processCharge = new ProcessChargeService(reservations, intents, publisher, clock);
        sendCharge = new SendChargeToGatewayService(intents, gateway, registerResult, failures);
        webhookHandler = new ChargePaymentWebhookHandler(intents, gateway, registerResult, failures);
    }

    private UUID registerCalculatedReservation() {
        ReservationInformation reservation = ChargeFixtures.calculatedReservation();
        reservations.save(reservation);
        reservations.saveCalculatedAmounts(reservation);
        return reservation.reservationId().value();
    }

    private ChargeIntent processAndSend(UUID reservationId, String operationKey) {
        processCharge.process(new ProcessChargeCommand(reservationId, "token-123", "CREDIT_CARD",
                Map.of("payer_email", PAYER_EMAIL, "last_four", "4242"), operationKey));
        ChargeCommand command = publisher.published().get(publisher.publishedCount() - 1);
        sendCharge.send(command);
        return intents.findById(command.chargeIntentId()).orElseThrow();
    }

    @Test
    void ce001_montos_autorizados_y_confirmados_sin_discrepancias() {
        UUID reservationId = registerCalculatedReservation();
        gateway.withSendResult(new ChargeGatewayResult("approved", EXTERNAL_REFERENCE, "accredited"));

        ChargeIntent intent = processAndSend(reservationId, "key-1");

        ChargeRecord record = records.findByChargeIntentId(intent.id()).orElseThrow();
        assertThat(intent.amount()).isEqualTo(ChargeFixtures.TOTAL);
        assertThat(record.amount()).isEqualTo(ChargeFixtures.TOTAL);
        assertThat(record.rentalAmount().add(record.insuranceAmount()).add(record.depositAmount()))
                .isEqualTo(record.amount());
    }

    @Test
    void ce002_trazabilidad_solicitud_resultado_e_intencion_registro() {
        UUID reservationId = registerCalculatedReservation();
        gateway.withSendResult(new ChargeGatewayResult("rejected", "987654", "cc_rejected"));
        ChargeIntent first = processAndSend(reservationId, "key-1");
        gateway.withSendResult(new ChargeGatewayResult("approved", "987655", "accredited"));
        ChargeIntent second = processAndSend(reservationId, "key-2");

        assertThat(intents.findByStatusIn(java.util.List.of(ChargeIntentStatus.REJECTED,
                ChargeIntentStatus.CAPTURED))).hasSize(2);
        assertThat(first.status()).isEqualTo(ChargeIntentStatus.REJECTED);
        assertThat(second.status()).isEqualTo(ChargeIntentStatus.CAPTURED);
        assertThat(records.findByChargeIntentId(first.id())).isEmpty();
        assertThat(records.findByChargeIntentId(second.id()).orElseThrow().chargeIntentId())
                .isEqualTo(second.id());
        assertThat(records.findByChargeIntentId(second.id()).orElseThrow().reservationId().value())
                .isEqualTo(reservationId);
    }

    @Test
    void ce003_fallas_de_comunicacion_sin_estado_indefinido() {
        UUID reservationId = registerCalculatedReservation();
        gateway.withSendFailure(new GatewayCommunicationException("The payment gateway did not answer"));

        assertThatExceptionOfType(GatewayCommunicationException.class)
                .isThrownBy(() -> processAndSend(reservationId, "key-1"));

        ChargeIntent intent = intents.findByStatusIn(java.util.List.of(ChargeIntentStatus.COMMUNICATION_ERROR))
                .get(0);
        assertThat(intent.status()).isEqualTo(ChargeIntentStatus.COMMUNICATION_ERROR);
        assertThat(intent.statusDetail()).isNotBlank();
        assertThat(records.count()).isZero();
    }

    @Test
    void ce004_rechazados_o_fallidos_no_son_cobros_exitosos() {
        UUID reservationId = registerCalculatedReservation();
        gateway.withSendResult(new ChargeGatewayResult("rejected", EXTERNAL_REFERENCE, "cc_rejected_bad_filled"));

        ChargeIntent intent = processAndSend(reservationId, "key-1");

        assertThat(intent.status()).isEqualTo(ChargeIntentStatus.REJECTED);
        assertThat(records.count()).isZero();
    }

    @Test
    void ce005_sin_datos_sensibles_persistidos_ni_en_logs() {
        UUID reservationId = registerCalculatedReservation();
        gateway.withSendResult(new ChargeGatewayResult("approved", EXTERNAL_REFERENCE, "accredited"));

        ChargeIntent intent = processAndSend(reservationId, "key-1");

        ChargeRecord record = records.findByChargeIntentId(intent.id()).orElseThrow();
        assertThat(record.toString()).doesNotContain("token-123");
        assertThat(intent.paymentMethod().toString()).doesNotContain("token-123");
        assertThat(publisher.published().get(0).toString()).doesNotContain("token-123");
        assertThat(record.paymentMethodType()).isEqualTo("CREDIT_CARD");
    }

    @Test
    void ce006_metadatos_enmascarados_conservados() {
        UUID reservationId = registerCalculatedReservation();
        gateway.withSendResult(new ChargeGatewayResult("approved", EXTERNAL_REFERENCE, "accredited"));

        ChargeIntent intent = processAndSend(reservationId, "key-1");

        ChargeRecord record = records.findByChargeIntentId(intent.id()).orElseThrow();
        assertThat(intent.paymentMethod().paymentMethodType()).isEqualTo("CREDIT_CARD");
        assertThat(intent.paymentMethod().lastFour()).isEqualTo("4242");
        assertThat(record.paymentMethodType()).isEqualTo("CREDIT_CARD");
        assertThat(record.lastFour()).isEqualTo("4242");
        assertThat(record.externalReference()).isEqualTo(EXTERNAL_REFERENCE);
    }

    @Test
    void ce007_expiracion_reportada_por_la_pasarela_registrada_sin_asumir_fondos() {
        UUID reservationId = registerCalculatedReservation();
        gateway.withSendResult(new ChargeGatewayResult("in_process", EXTERNAL_REFERENCE, "pending"));
        ChargeIntent intent = processAndSend(reservationId, "key-1");
        gateway.withFetchResult(new ChargeGatewayResult("expired", EXTERNAL_REFERENCE, "expired"));

        webhookHandler.handle(new GatewayWebhookNotification("payment", "payment.updated", EXTERNAL_REFERENCE));

        ChargeIntent updated = intents.findById(intent.id()).orElseThrow();
        assertThat(updated.status()).isEqualTo(ChargeIntentStatus.EXPIRED);
        assertThat(records.count()).isZero();
    }

    @Test
    void noRegistraElCobroDeUnaReservaSinValorCalculado() {
        ReservationInformation reservation = ChargeFixtures.calculatedReservation();
        reservations.save(reservation);

        assertThatExceptionOfType(
                com.seashare.seasharem3.domain.exception.ReservationValueNotCalculatedException.class)
                .isThrownBy(() -> processCharge.process(new ProcessChargeCommand(
                        reservation.reservationId().value(), "token-123", "CREDIT_CARD",
                        Map.of("payer_email", PAYER_EMAIL), "key-1")));

        assertThat(publisher.publishedCount()).isZero();
        assertThat(gateway.requests()).isEmpty();
    }
}
