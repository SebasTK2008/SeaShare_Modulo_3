package com.seashare.seasharem3.acceptance;

import com.seashare.seasharem3.application.dto.DepositTrackingResult;
import com.seashare.seasharem3.application.dto.OpenDepositTrackingCommand;
import com.seashare.seasharem3.application.dto.ProcessChargeCommand;
import com.seashare.seasharem3.application.dto.RefundRequestCommand;
import com.seashare.seasharem3.application.dto.RefundRequestResult;
import com.seashare.seasharem3.application.dto.ReservationStatusCommand;
import com.seashare.seasharem3.application.dto.SettlementCommand;
import com.seashare.seasharem3.application.dto.SettlementRequestResult;
import com.seashare.seasharem3.application.port.in.OpenDepositTrackingUseCase;
import com.seashare.seasharem3.application.port.in.ProcessChargeUseCase;
import com.seashare.seasharem3.application.port.in.RequestRefundUseCase;
import com.seashare.seasharem3.application.port.in.RequestSettlementUseCase;
import com.seashare.seasharem3.application.service.HandleReservationStatusService;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.service.ReservationStatusPolicy;
import com.seashare.seasharem3.domain.valueobject.DepositDispositionStatus;
import com.seashare.seasharem3.domain.valueobject.RefundIntentStatus;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.domain.valueobject.ReservationStatus;
import com.seashare.seasharem3.domain.valueobject.SettlementScope;
import com.seashare.seasharem3.domain.valueobject.SettlementStatus;
import com.seashare.seasharem3.domain.valueobject.SettlementTrigger;
import com.seashare.seasharem3.support.ChargeFixtures;
import com.seashare.seasharem3.support.InMemoryFailureRecorder;
import com.seashare.seasharem3.support.InMemoryReservationInformationRepository;
import com.seashare.seasharem3.support.InMemoryReservationStatusLogRepository;
import com.seashare.seasharem3.support.NoOpReservationLockPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aceptación de UC07 sobre el servicio real, la política real y el log en memoria, con dobles de
 * UC05/UC08/UC09/UC10: CE-001 a CE-007 del SPEC 07.
 */
class Uc07AcceptanceTest {

    private static final Instant STATUS_CHANGED_AT = Instant.parse("2026-12-05T14:30:00Z");
    private static final Instant NOW = Instant.parse("2026-12-05T14:30:01Z");

    private final InMemoryReservationInformationRepository reservations =
            new InMemoryReservationInformationRepository();
    private final InMemoryReservationStatusLogRepository statusLog = new InMemoryReservationStatusLogRepository();
    private final InMemoryFailureRecorder failures = new InMemoryFailureRecorder();
    private final NoOpReservationLockPort reservationLock = new NoOpReservationLockPort();
    private final RecordingProcessCharge processCharge = new RecordingProcessCharge();
    private final RecordingRefund requestRefund = new RecordingRefund();
    private final RecordingSettlement requestSettlement = new RecordingSettlement();
    private final RecordingDepositTracking openDepositTracking = new RecordingDepositTracking();

    private ReservationInformation reservation;
    private HandleReservationStatusService service;

    @BeforeEach
    void setUp() {
        reservation = ChargeFixtures.calculatedReservation();
        reservations.save(ReservationInformation.create(reservation.reservationId(), reservation.boatId(),
                reservation.baseRate(), null, reservation.startDate(), reservation.endDate(),
                reservation.passengers(), reservation.ownerId(), reservation.maxCapacity()));
        reservations.saveCalculatedAmounts(reservation);

        service = new HandleReservationStatusService(reservations, statusLog, reservationLock, failures,
                processCharge, requestRefund, requestSettlement, openDepositTracking,
                new ReservationStatusPolicy(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void ce001_decision_financiera_correcta_por_estado() {
        Map<ReservationStatus, Calls> expected = new LinkedHashMap<>();
        expected.put(ReservationStatus.AVAILABLE, Calls.none());
        expected.put(ReservationStatus.INITIATED, Calls.none());
        expected.put(ReservationStatus.RESERVED, Calls.none());
        expected.put(ReservationStatus.IN_NAVIGATION, Calls.none());
        expected.put(ReservationStatus.PENDING, Calls.charge());
        expected.put(ReservationStatus.CANCELLED_FLEXIBLE, Calls.refund("CANCELLED_FLEXIBLE"));
        expected.put(ReservationStatus.CANCELLED_MODERATE,
                Calls.both("CANCELLED_MODERATE", "CANCELLED_MODERATE"));
        expected.put(ReservationStatus.CANCELLED_STRICT, Calls.both("CANCELLED_STRICT", "CANCELLED_STRICT"));
        expected.put(ReservationStatus.CANCELLED_BY_HOST, Calls.refund("CANCELLED_BY_HOST"));
        expected.put(ReservationStatus.COMPLETED, Calls.settlement("COMPLETED"));

        expected.forEach((status, calls) -> {
            reset();
            service.handle(command(status));
            assertThat(observed()).as("plan of %s", status).isEqualTo(calls);
        });
    }

    @Test
    void ce002_cancelaciones_disparan_exactamente_las_operaciones_esperadas() {
        service.handle(command(ReservationStatus.CANCELLED_FLEXIBLE));
        service.handle(command(ReservationStatus.CANCELLED_MODERATE));
        service.handle(command(ReservationStatus.CANCELLED_STRICT));
        service.handle(command(ReservationStatus.CANCELLED_BY_HOST));

        assertThat(requestRefund.commands()).extracting(RefundRequestCommand::trigger).containsExactly(
                com.seashare.seasharem3.domain.valueobject.RefundTrigger.CANCELLED_FLEXIBLE,
                com.seashare.seasharem3.domain.valueobject.RefundTrigger.CANCELLED_MODERATE,
                com.seashare.seasharem3.domain.valueobject.RefundTrigger.CANCELLED_STRICT,
                com.seashare.seasharem3.domain.valueobject.RefundTrigger.CANCELLED_BY_HOST);
        assertThat(requestSettlement.commands()).extracting(SettlementCommand::trigger).containsExactly(
                SettlementTrigger.CANCELLED_MODERATE, SettlementTrigger.CANCELLED_STRICT);
        assertThat(requestSettlement.commands()).allSatisfy(command ->
                assertThat(command.scope()).isEqualTo(SettlementScope.CANCELLATION_COMPENSATION));
        assertThat(openDepositTracking.commands()).isEmpty();
        assertThat(statusLog.rows()).hasSize(6);
    }

    @Test
    void ce003_cero_operaciones_sobre_el_deposito_por_completada_o_estados_operativos() {
        for (ReservationStatus status : List.of(ReservationStatus.AVAILABLE, ReservationStatus.INITIATED,
                ReservationStatus.RESERVED, ReservationStatus.IN_NAVIGATION, ReservationStatus.PENDING)) {
            service.handle(command(status));
        }

        assertThat(openDepositTracking.commands()).isEmpty();

        service.handle(command(ReservationStatus.COMPLETED));

        assertThat(openDepositTracking.commands()).singleElement()
                .satisfies(command -> assertThat(command.target()).isEqualTo(DepositDispositionStatus.HELD));
        assertThat(requestRefund.commands()).isEmpty();
        assertThat(requestSettlement.commands()).allSatisfy(command ->
                assertThat(command.scope()).isNotEqualTo(SettlementScope.DEPOSIT));
    }

    @Test
    void ce004_fallos_por_informacion_ausente_se_registran_sin_ejecuciones_parciales() {
        ReservationId unknown = new ReservationId(UUID.randomUUID());
        ReservationInformation notCalculated = ReservationInformation.create(reservation.reservationId(),
                reservation.boatId(), reservation.baseRate(), null, reservation.startDate(),
                reservation.endDate(), reservation.passengers(), reservation.ownerId(),
                reservation.maxCapacity());
        reservations.saveCalculatedAmounts(notCalculated);

        service.handle(new ReservationStatusCommand(unknown.value(), ReservationStatus.CANCELLED_MODERATE,
                STATUS_CHANGED_AT, null, null, null));
        service.handle(command(ReservationStatus.CANCELLED_MODERATE));

        assertThat(failures.failures()).extracting(InMemoryFailureRecorder.RecordedFailure::reason)
                .containsExactly("RESERVATION_INFO_NOT_FOUND", "RESERVATION_INFO_INCOMPLETE");
        assertThat(requestRefund.commands()).isEmpty();
        assertThat(requestSettlement.commands()).isEmpty();
        assertThat(statusLog.rows()).isEmpty();
    }

    @Test
    void ce005_completada_liquida_estandar_y_conserva_el_deposito() {
        service.handle(command(ReservationStatus.COMPLETED));

        assertThat(requestSettlement.commands()).singleElement().satisfies(command -> {
            assertThat(command.trigger()).isEqualTo(SettlementTrigger.COMPLETED);
            assertThat(command.scope()).isEqualTo(SettlementScope.STANDARD);
        });
        assertThat(openDepositTracking.commands()).singleElement().satisfies(command -> {
            assertThat(command.target()).isEqualTo(DepositDispositionStatus.HELD);
            assertThat(command.completedAt()).isEqualTo(STATUS_CHANGED_AT);
        });
        assertThat(statusLog.rows()).singleElement().satisfies(row -> {
            assertThat(row.processedAt()).isNotNull();
            assertThat(row.outcome()).isEqualTo("delegated");
        });
    }

    @Test
    void ce006_cero_decisiones_sobre_danos_o_destino_del_deposito() {
        for (ReservationStatus status : ReservationStatus.values()) {
            service.handle(command(status));
        }

        assertThat(openDepositTracking.commands()).allSatisfy(command -> {
            assertThat(command.target()).isEqualTo(DepositDispositionStatus.HELD);
            assertThat(command.disputeId()).isNull();
            assertThat(command.disputeStatus()).isNull();
        });
        assertThat(requestSettlement.commands()).allSatisfy(command ->
                assertThat(command.scope()).isNotEqualTo(SettlementScope.DEPOSIT));
    }

    @Test
    void ce007_iniciada_pendiente_sin_reinicio_ttl_identidad_y_exclusion_mutua() {
        service.handle(command(ReservationStatus.INITIATED));
        assertThat(processCharge.commands()).isEmpty();

        service.handle(pendingCommand(STATUS_CHANGED_AT));
        service.handle(pendingCommand(STATUS_CHANGED_AT));
        assertThat(processCharge.commands()).hasSize(1);

        service.handle(pendingCommand(STATUS_CHANGED_AT.plusSeconds(120)));
        assertThat(processCharge.commands()).hasSize(2);
        assertThat(processCharge.commands()).allSatisfy(command ->
                assertThat(command.paymentTokenRef()).isEqualTo("tok_12345"));

        assertThat(statusLog.rows()).hasSize(2).allSatisfy(row -> {
            assertThat(row.status()).isEqualTo(ReservationStatus.PENDING);
            assertThat(row.processedAt()).isNotNull();
        });
        assertThat(statusLog.rows()).extracting(row -> row.idempotencyKey().value()).doesNotHaveDuplicates();

        openDepositTracking.resolveAs(DepositDispositionStatus.REFUND_REQUESTED);
        service.handle(command(ReservationStatus.COMPLETED));
        assertThat(openDepositTracking.commands()).singleElement();
        assertThat(failures.failures()).singleElement()
                .satisfies(failure -> assertThat(failure.payloadRef()).contains("deposit_disposition=REFUND_REQUESTED"));
    }

    private void reset() {
        processCharge.clear();
        requestRefund.clear();
        requestSettlement.clear();
        openDepositTracking.clear();
        statusLog.clear();
        failures.clear();
    }

    private Calls observed() {
        return new Calls(processCharge.commands().size(),
                requestRefund.commands().stream().map(command -> command.trigger().name()).toList(),
                requestSettlement.commands().stream().map(command -> command.trigger().name()).toList(),
                requestSettlement.commands().stream().map(SettlementCommand::scope).toList(),
                openDepositTracking.commands().size());
    }

    private ReservationStatusCommand command(ReservationStatus status) {
        return new ReservationStatusCommand(reservation.reservationId().value(), status, STATUS_CHANGED_AT,
                null, null, null);
    }

    private ReservationStatusCommand pendingCommand(Instant statusChangedAt) {
        return new ReservationStatusCommand(reservation.reservationId().value(), ReservationStatus.PENDING,
                statusChangedAt, "tok_12345", "CREDIT_CARD", Map.of("payer_email", "arrendatario@example.com"));
    }

    /** Operaciones observadas en una ejecución, para comparar el plan esperado por estado. */
    private record Calls(int charges, List<String> refunds, List<String> settlements,
            List<SettlementScope> scopes, int depositOpenings) {

        static Calls none() {
            return new Calls(0, List.of(), List.of(), List.of(), 0);
        }

        static Calls charge() {
            return new Calls(1, List.of(), List.of(), List.of(), 0);
        }

        static Calls refund(String trigger) {
            return new Calls(0, List.of(trigger), List.of(), List.of(), 0);
        }

        static Calls both(String refundTrigger, String settlementTrigger) {
            return new Calls(0, List.of(refundTrigger), List.of(settlementTrigger),
                    List.of(SettlementScope.CANCELLATION_COMPENSATION), 0);
        }

        /** La liquidación estándar de COMPLETED siempre va seguida de la apertura del depósito. */
        static Calls settlement(String trigger) {
            return new Calls(0, List.of(), List.of(trigger), List.of(SettlementScope.STANDARD), 1);
        }
    }

    private static final class RecordingProcessCharge implements ProcessChargeUseCase {

        private final List<ProcessChargeCommand> commands = new ArrayList<>();

        @Override
        public void process(ProcessChargeCommand command) {
            commands.add(command);
        }

        List<ProcessChargeCommand> commands() {
            return List.copyOf(commands);
        }

        void clear() {
            commands.clear();
        }
    }

    private static final class RecordingRefund implements RequestRefundUseCase {

        private final List<RefundRequestCommand> commands = new ArrayList<>();
        private RefundIntentStatus resulting = RefundIntentStatus.PENDING_SEND;

        @Override
        public RefundRequestResult request(RefundRequestCommand command) {
            commands.add(command);
            return new RefundRequestResult(UUID.randomUUID(), resulting);
        }

        List<RefundRequestCommand> commands() {
            return List.copyOf(commands);
        }

        void clear() {
            commands.clear();
        }
    }

    private static final class RecordingSettlement implements RequestSettlementUseCase {

        private final List<SettlementCommand> commands = new ArrayList<>();

        @Override
        public SettlementRequestResult request(SettlementCommand command) {
            commands.add(command);
            return new SettlementRequestResult(UUID.randomUUID(), SettlementStatus.PENDING_SEND);
        }

        List<SettlementCommand> commands() {
            return List.copyOf(commands);
        }

        void clear() {
            commands.clear();
        }
    }

    private static final class RecordingDepositTracking implements OpenDepositTrackingUseCase {

        private final List<OpenDepositTrackingCommand> commands = new ArrayList<>();
        private DepositDispositionStatus resulting = DepositDispositionStatus.HELD;

        @Override
        public DepositTrackingResult open(OpenDepositTrackingCommand command) {
            commands.add(command);
            return new DepositTrackingResult(command.reservationId(), resulting, true);
        }

        void resolveAs(DepositDispositionStatus status) {
            this.resulting = status;
        }

        List<OpenDepositTrackingCommand> commands() {
            return List.copyOf(commands);
        }

        void clear() {
            commands.clear();
        }
    }
}
