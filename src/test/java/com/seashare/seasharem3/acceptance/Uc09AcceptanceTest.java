package com.seashare.seasharem3.acceptance;

import com.seashare.seasharem3.application.dto.RefundGatewayResult;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.application.port.out.ChargeRecordQueryPort;
import com.seashare.seasharem3.application.port.out.RefundGatewayPort;
import com.seashare.seasharem3.application.port.out.RefundIntentRepository;
import com.seashare.seasharem3.application.port.out.RefundRecordRepository;
import com.seashare.seasharem3.application.port.out.ReservationInformationRepository;
import com.seashare.seasharem3.application.service.RegisterRefundResultService;
import com.seashare.seasharem3.application.service.SendRefundToGatewayService;
import com.seashare.seasharem3.domain.exception.GatewayCommunicationException;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.model.ChargeRecord;
import com.seashare.seasharem3.domain.model.RefundIntent;
import com.seashare.seasharem3.domain.model.RefundRecord;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.RefundIntentStatus;
import com.seashare.seasharem3.domain.valueobject.RefundScope;
import com.seashare.seasharem3.domain.valueobject.RefundTrigger;
import com.seashare.seasharem3.support.ChargeFixtures;
import com.seashare.seasharem3.support.InMemoryFailureRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas de aceptación de UC09 (CE-001 a CE-006): el reembolso confirmado produce un registro con
 * el monto neto correcto y trazable a su intención; un rechazo o una falla no producen registro.
 *
 * <p>Fuente: UC09 plan T029/T030 y SPEC 9 Criterios de Éxito.</p>
 */
class Uc09AcceptanceTest {

    private static final ReservationInformation RESERVATION = ChargeFixtures.calculatedReservation();
    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

    private final RefundIntentRepository refundIntentRepository = mock(RefundIntentRepository.class);
    private final RefundRecordRepository refundRecordRepository = mock(RefundRecordRepository.class);
    private final ChargeIntentRepository chargeIntentRepository = mock(ChargeIntentRepository.class);
    private final ChargeRecordQueryPort chargeRecordQueryPort = mock(ChargeRecordQueryPort.class);
    private final ReservationInformationRepository reservationInformationRepository = mock(
            ReservationInformationRepository.class);
    private final RefundGatewayPort refundGatewayPort = mock(RefundGatewayPort.class);
    private final InMemoryFailureRecorder failures = new InMemoryFailureRecorder();

    private RegisterRefundResultService registerResult;
    private SendRefundToGatewayService sendRefund;
    private ChargeIntent charge;

    @BeforeEach
    void setUp() {
        registerResult = new RegisterRefundResultService(refundIntentRepository, refundRecordRepository,
                chargeIntentRepository, chargeRecordQueryPort, reservationInformationRepository, failures,
                Clock.fixed(NOW, ZoneOffset.UTC));
        sendRefund = new SendRefundToGatewayService(refundIntentRepository, chargeIntentRepository, refundGatewayPort,
                registerResult, failures);
        charge = ChargeFixtures.capturedIntent(RESERVATION, "mp-payment-1");
        ChargeRecord chargeRecord = ChargeRecord.create(UUID.randomUUID(), charge, RESERVATION, NOW);
        when(chargeIntentRepository.findById(charge.id())).thenReturn(Optional.of(charge));
        when(chargeRecordQueryPort.findByChargeIntentId(charge.id())).thenReturn(Optional.of(chargeRecord));
        when(refundIntentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(refundRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(refundRecordRepository.findByRefundIntentId(any())).thenReturn(Optional.empty());
    }

    private RefundIntent pendingIntent() {
        return RefundIntent.create(UUID.randomUUID(), RESERVATION.reservationId(), RefundTrigger.DISPUTE_REJECTED,
                RefundScope.DEPOSIT_ONLY, new Money(new BigDecimal("30000.00")), charge.id(),
                new IdempotencyKey("refund:DISPUTE_REJECTED:op-1"), UUID.randomUUID(), NOW);
    }

    @Test
    void ce001_monto_confirmado_y_neto_liberado_sin_discrepancias() {
        RefundIntent intent = pendingIntent().transitionTo(RefundIntentStatus.IN_PROCESS).withExternalReference("rf-1");
        when(refundIntentRepository.findByExternalReference("rf-1")).thenReturn(Optional.of(intent));

        registerResult.register(new RefundGatewayResult("approved", "rf-1",
                new Money(new BigDecimal("30000.00")), "accredited", new Money(new BigDecimal("1000.00"))));

        var captor = org.mockito.ArgumentCaptor.forClass(RefundRecord.class);
        verify(refundRecordRepository).save(captor.capture());
        assertThat(captor.getValue().amount().amount()).isEqualByComparingTo("30000");
        assertThat(captor.getValue().releasedAmount().amount()).isEqualByComparingTo("29000");
    }

    @Test
    void ce002_trazabilidad_de_la_intencion_al_registro() {
        RefundIntent intent = pendingIntent().transitionTo(RefundIntentStatus.IN_PROCESS).withExternalReference("rf-2");
        when(refundIntentRepository.findByExternalReference("rf-2")).thenReturn(Optional.of(intent));

        registerResult.register(new RefundGatewayResult("approved", "rf-2",
                new Money(new BigDecimal("30000.00")), null, null));

        var captor = org.mockito.ArgumentCaptor.forClass(RefundRecord.class);
        verify(refundRecordRepository).save(captor.capture());
        assertThat(captor.getValue().refundIntentId()).isEqualTo(intent.id());
        assertThat(captor.getValue().reservationId()).isEqualTo(intent.reservationId());
        assertThat(captor.getValue().disputeId()).isEqualTo(intent.disputeId());
    }

    @Test
    void ce003_una_falla_de_comunicacion_deja_un_estado_explicito_sin_registro() {
        RefundIntent intent = pendingIntent();
        when(refundIntentRepository.findByIdForUpdate(intent.id())).thenReturn(Optional.of(intent));
        when(refundGatewayPort.send(any())).thenThrow(new GatewayCommunicationException("timeout"));

        assertThatThrownBy(() -> sendRefund.send(intent.id()))
                .isInstanceOf(GatewayCommunicationException.class);

        var captor = org.mockito.ArgumentCaptor.forClass(RefundIntent.class);
        verify(refundIntentRepository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(RefundIntentStatus.COMMUNICATION_ERROR);
        verify(refundRecordRepository, never()).save(any());
    }

    @Test
    void ce004_un_rechazo_no_es_un_reembolso_exitoso() {
        RefundIntent intent = pendingIntent().transitionTo(RefundIntentStatus.IN_PROCESS).withExternalReference("rf-3");
        when(refundIntentRepository.findByExternalReference("rf-3")).thenReturn(Optional.of(intent));

        registerResult.register(new RefundGatewayResult("rejected", "rf-3", null, "rejected_by_issuer", null));

        verify(refundRecordRepository, never()).save(any());
        verify(refundIntentRepository).save(any(RefundIntent.class));
    }

    @Test
    void ce005_el_registro_no_conserva_datos_de_pago_sensibles() {
        assertThat(fieldNames(RefundRecord.class))
                .noneMatch(name -> name.toLowerCase().contains("token")
                        || name.toLowerCase().contains("cvv") || name.toLowerCase().contains("pan"));
    }

    @Test
    void ce006_un_resultado_repetido_no_duplica_el_registro() {
        RefundIntent intent = pendingIntent().transitionTo(RefundIntentStatus.IN_PROCESS).withExternalReference("rf-4");
        when(refundIntentRepository.findByExternalReference("rf-4")).thenReturn(Optional.of(intent));
        when(refundRecordRepository.findByRefundIntentId(intent.id()))
                .thenReturn(Optional.of(RefundRecord.create(UUID.randomUUID(),
                        intent.transitionTo(RefundIntentStatus.COMPLETED),
                        RESERVATION.ownerId(), RESERVATION.boatId(), new Money(new BigDecimal("30000.00")), null,
                        "accredited", "rf-4", NOW)));

        registerResult.register(new RefundGatewayResult("approved", "rf-4",
                new Money(new BigDecimal("30000.00")), null, null));

        verify(refundRecordRepository, never()).save(any());
    }

    private static java.util.List<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(Field::getName).toList();
    }
}
