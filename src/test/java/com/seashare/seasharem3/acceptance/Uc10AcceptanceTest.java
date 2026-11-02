package com.seashare.seasharem3.acceptance;

import com.seashare.seasharem3.application.dto.GatewaySettlementResult;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.application.port.out.ChargeRecordQueryPort;
import com.seashare.seasharem3.application.port.out.CommissionRecordRepository;
import com.seashare.seasharem3.application.port.out.ReservationInformationRepository;
import com.seashare.seasharem3.application.port.out.SettlementGatewayPort;
import com.seashare.seasharem3.application.port.out.SettlementIntentRepository;
import com.seashare.seasharem3.application.port.out.SettlementRecordRepository;
import com.seashare.seasharem3.application.service.RegisterSettlementResultService;
import com.seashare.seasharem3.application.service.SendSettlementToGatewayService;
import com.seashare.seasharem3.domain.exception.GatewayCommunicationException;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.model.ChargeRecord;
import com.seashare.seasharem3.domain.model.CommissionRecord;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.model.SettlementIntent;
import com.seashare.seasharem3.domain.model.SettlementRecord;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.SettlementScope;
import com.seashare.seasharem3.domain.valueobject.SettlementStatus;
import com.seashare.seasharem3.domain.valueobject.SettlementTrigger;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas de aceptación de UC10 (CE-001 a CE-006): la dispersión confirmada produce un registro con
 * su desglose y, en la liquidación estándar, la comisión asociada; un rechazo o una falla no crean
 * registros financieros.
 *
 * <p>Fuente: UC10 plan T021/T022 y SPEC 10 Criterios de Éxito.</p>
 */
class Uc10AcceptanceTest {

    private static final ReservationInformation RESERVATION = ChargeFixtures.calculatedReservation();
    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

    private final SettlementIntentRepository settlementIntentRepository = mock(SettlementIntentRepository.class);
    private final SettlementRecordRepository settlementRecordRepository = mock(SettlementRecordRepository.class);
    private final CommissionRecordRepository commissionRecordRepository = mock(CommissionRecordRepository.class);
    private final ChargeIntentRepository chargeIntentRepository = mock(ChargeIntentRepository.class);
    private final ChargeRecordQueryPort chargeRecordQueryPort = mock(ChargeRecordQueryPort.class);
    private final ReservationInformationRepository reservationInformationRepository = mock(
            ReservationInformationRepository.class);
    private final SettlementGatewayPort settlementGatewayPort = mock(SettlementGatewayPort.class);
    private final InMemoryFailureRecorder failures = new InMemoryFailureRecorder();

    private RegisterSettlementResultService registerResult;
    private SendSettlementToGatewayService sendSettlement;
    private ChargeIntent charge;

    @BeforeEach
    void setUp() {
        registerResult = new RegisterSettlementResultService(settlementIntentRepository, settlementRecordRepository,
                commissionRecordRepository, chargeIntentRepository, chargeRecordQueryPort,
                reservationInformationRepository, failures, Clock.fixed(NOW, ZoneOffset.UTC));
        sendSettlement = new SendSettlementToGatewayService(settlementIntentRepository, chargeIntentRepository,
                settlementGatewayPort, registerResult, failures, 123456789L);
        charge = ChargeFixtures.capturedIntent(RESERVATION, "mp-payment-1");
        ChargeRecord chargeRecord = ChargeRecord.create(UUID.randomUUID(), charge, RESERVATION, NOW);
        when(settlementIntentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chargeIntentRepository.findById(charge.id())).thenReturn(Optional.of(charge));
        when(chargeRecordQueryPort.findByChargeIntentId(charge.id())).thenReturn(Optional.of(chargeRecord));
        when(settlementRecordRepository.findBySettlementIntentId(any())).thenReturn(Optional.empty());
        when(settlementRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(commissionRecordRepository.findBySettlementRecordId(any())).thenReturn(Optional.empty());
        when(commissionRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private SettlementIntent intent(SettlementScope scope, SettlementStatus status) {
        SettlementIntent created = SettlementIntent.create(UUID.randomUUID(), RESERVATION.reservationId(),
                SettlementTrigger.COMPLETED, scope, new Money(new BigDecimal("660000.00")),
                new Money(new BigDecimal("180000.00")), Money.ZERO, charge.id(),
                new IdempotencyKey("settlement:COMPLETED:op-1"), null, NOW);
        return status == SettlementStatus.PENDING_SEND ? created : created.transitionTo(status);
    }

    @Test
    void ce001_la_dispersion_confirmada_conserva_el_monto_y_el_desglose() {
        when(settlementIntentRepository.findByExternalReference("st-1"))
                .thenReturn(Optional.of(intent(SettlementScope.STANDARD, SettlementStatus.IN_PROCESS)));

        registerResult.register(new GatewaySettlementResult("approved", "st-1", "accredited",
                new Money(new BigDecimal("660000.00"))));

        var record = org.mockito.ArgumentCaptor.forClass(SettlementRecord.class);
        verify(settlementRecordRepository).save(record.capture());
        assertThat(record.getValue().amount().amount()).isEqualByComparingTo("660000");
        assertThat(record.getValue().insuranceAmount().amount()).isEqualByComparingTo("60000");
    }

    @Test
    void ce002_trazabilidad_de_la_intencion_al_registro() {
        SettlementIntent intent = intent(SettlementScope.STANDARD, SettlementStatus.IN_PROCESS)
                .withExternalReference("st-2");
        when(settlementIntentRepository.findByExternalReference("st-2")).thenReturn(Optional.of(intent));

        registerResult.register(new GatewaySettlementResult("approved", "st-2", null,
                new Money(new BigDecimal("660000.00"))));

        var record = org.mockito.ArgumentCaptor.forClass(SettlementRecord.class);
        verify(settlementRecordRepository).save(record.capture());
        assertThat(record.getValue().settlementIntentId()).isEqualTo(intent.id());
        assertThat(record.getValue().reservationId()).isEqualTo(intent.reservationId());
    }

    @Test
    void ce003_una_falla_de_comunicacion_deja_un_estado_explicito_sin_registro() {
        SettlementIntent intent = intent(SettlementScope.STANDARD, SettlementStatus.PENDING_SEND);
        when(settlementIntentRepository.findByIdForUpdate(intent.id())).thenReturn(Optional.of(intent));
        when(settlementGatewayPort.send(any())).thenThrow(new GatewayCommunicationException("timeout"));

        assertThatThrownBy(() -> sendSettlement.send(intent.id()))
                .isInstanceOf(GatewayCommunicationException.class);

        var captor = org.mockito.ArgumentCaptor.forClass(SettlementIntent.class);
        verify(settlementIntentRepository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(SettlementStatus.COMMUNICATION_ERROR);
        verify(settlementRecordRepository, never()).save(any());
    }

    @Test
    void ce004_un_rechazo_no_crea_registro_ni_comision() {
        when(settlementIntentRepository.findByExternalReference("st-3"))
                .thenReturn(Optional.of(intent(SettlementScope.STANDARD, SettlementStatus.IN_PROCESS)));

        registerResult.register(new GatewaySettlementResult("rejected", "st-3", "rejected_by_issuer", null));

        verify(settlementRecordRepository, never()).save(any());
        verify(commissionRecordRepository, never()).save(any());
        verify(settlementIntentRepository).save(any(SettlementIntent.class));
    }

    @Test
    void ce005_los_registros_no_conservan_datos_de_pago_sensibles() {
        assertThat(fieldNames(SettlementRecord.class))
                .noneMatch(name -> name.toLowerCase().contains("token")
                        || name.toLowerCase().contains("cvv") || name.toLowerCase().contains("pan"));
        assertThat(fieldNames(CommissionRecord.class))
                .noneMatch(name -> name.toLowerCase().contains("token"));
    }

    @Test
    void ce006_la_comision_queda_relacionada_con_su_registro_de_dispersion() {
        when(settlementIntentRepository.findByExternalReference("st-4"))
                .thenReturn(Optional.of(intent(SettlementScope.STANDARD, SettlementStatus.IN_PROCESS)));

        registerResult.register(new GatewaySettlementResult("approved", "st-4", "accredited",
                new Money(new BigDecimal("660000.00"))));

        var settlement = org.mockito.ArgumentCaptor.forClass(SettlementRecord.class);
        var commission = org.mockito.ArgumentCaptor.forClass(CommissionRecord.class);
        verify(settlementRecordRepository).save(settlement.capture());
        verify(commissionRecordRepository).save(commission.capture());
        assertThat(commission.getValue().settlementRecordId()).isEqualTo(settlement.getValue().id());
        assertThat(commission.getValue().settlementIntentId()).isEqualTo(settlement.getValue().settlementIntentId());
    }

    private static java.util.List<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(Field::getName).toList();
    }
}
