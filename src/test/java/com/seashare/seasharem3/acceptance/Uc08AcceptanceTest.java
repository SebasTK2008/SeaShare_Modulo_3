package com.seashare.seasharem3.acceptance;

import com.seashare.seasharem3.application.dto.DisputeNotificationCommand;
import com.seashare.seasharem3.application.dto.RefundRequestCommand;
import com.seashare.seasharem3.application.dto.SettlementCommand;
import com.seashare.seasharem3.application.dto.SettlementRequestResult;
import com.seashare.seasharem3.application.port.in.RequestRefundUseCase;
import com.seashare.seasharem3.application.port.in.RequestSettlementUseCase;
import com.seashare.seasharem3.application.service.DisputeInformationService;
import com.seashare.seasharem3.domain.exception.DisputeNotificationValidationException;
import com.seashare.seasharem3.domain.model.DepositDisposition;
import com.seashare.seasharem3.domain.valueobject.DepositDispositionStatus;
import com.seashare.seasharem3.domain.valueobject.DisputeId;
import com.seashare.seasharem3.domain.valueobject.DisputeStatus;
import com.seashare.seasharem3.domain.valueobject.RefundTrigger;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.domain.valueobject.SettlementScope;
import com.seashare.seasharem3.domain.valueobject.SettlementStatus;
import com.seashare.seasharem3.domain.valueobject.SettlementTrigger;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto.DisputeNotificationMessage;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto.DisputeNotificationMessageMapper;
import com.seashare.seasharem3.support.InMemoryDepositDispositionRepository;
import com.seashare.seasharem3.support.InMemoryDisputeEventLogRepository;
import com.seashare.seasharem3.support.InMemoryFailureRecorder;
import com.seashare.seasharem3.support.NoOpReservationLockPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aceptacion de UC08 sobre el servicio real con repositorios en memoria y dobles de UC09/UC10:
 * estados canonicos, PENDING sin dinero, consecuencias finales unicas, ausencia de montos,
 * idempotencia por event_key y rechazo de sub-resultados.
 */
class Uc08AcceptanceTest {

    private static final ReservationId RESERVATION = new ReservationId(UUID.randomUUID());
    private static final ReservationId OTHER_RESERVATION = new ReservationId(UUID.randomUUID());
    private static final DisputeId DISPUTE = new DisputeId(UUID.randomUUID());
    private static final Instant STATUS_CHANGED_AT = Instant.parse("2026-12-23T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-12-23T10:00:01Z");

    private final InMemoryDisputeEventLogRepository eventLog = new InMemoryDisputeEventLogRepository();
    private final InMemoryDepositDispositionRepository deposit = new InMemoryDepositDispositionRepository();
    private final InMemoryFailureRecorder failures = new InMemoryFailureRecorder();
    private final RecordingRefundUseCase refund = new RecordingRefundUseCase(deposit);
    private final RecordingSettlementUseCase settlement = new RecordingSettlementUseCase(deposit);
    private final DisputeNotificationMessageMapper mapper = new DisputeNotificationMessageMapper();

    private DisputeInformationService service;

    @BeforeEach
    void setUp() {
        service = new DisputeInformationService(eventLog, deposit, new NoOpReservationLockPort(), failures,
                refund, settlement, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void ce001_solo_estados_canonicos() {
        for (DisputeStatus status : DisputeStatus.values()) {
            assertThatCode(() -> mapper.toCommand(message(status))).doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> mapper.toCommand(message("PARTIALLY_RELEASED")))
                .isInstanceOf(DisputeNotificationValidationException.class);

        assertThat(refund.commands()).isEmpty();
        assertThat(settlement.commands()).isEmpty();
    }

    @Test
    void ce002_pendiente_no_mueve_dinero() {
        service.handle(command(RESERVATION, DisputeStatus.PENDING, "evt-1"));
        service.handle(command(RESERVATION, DisputeStatus.PENDING, "evt-2"));

        assertThat(refund.commands()).isEmpty();
        assertThat(settlement.commands()).isEmpty();
        assertThat(deposit.findByReservationId(RESERVATION)).isEmpty();
        assertThat(eventLog.events()).hasSize(2).allSatisfy(event -> assertThat(event.processedAt()).isNotNull());
    }

    @Test
    void ce003_finales_generan_una_sola_consecuencia_total() {
        service.handle(command(RESERVATION, DisputeStatus.REJECTED, "evt-r"));
        service.handle(command(OTHER_RESERVATION, DisputeStatus.COMPLETED, "evt-c"));

        assertThat(refund.commands()).singleElement().satisfies(command -> {
            assertThat(command.reservationId()).isEqualTo(RESERVATION.value());
            assertThat(command.trigger()).isEqualTo(RefundTrigger.DISPUTE_REJECTED);
            assertThat(command.disputeId()).isEqualTo(DISPUTE.value());
            assertThat(command.operationKey()).isEqualTo("evt-r");
        });
        assertThat(settlement.commands()).singleElement().satisfies(command -> {
            assertThat(command.reservationId()).isEqualTo(OTHER_RESERVATION);
            assertThat(command.trigger()).isEqualTo(SettlementTrigger.DISPUTE_COMPLETED);
            assertThat(command.scope()).isEqualTo(SettlementScope.DEPOSIT);
            assertThat(command.disputeId()).isEqualTo(DISPUTE.value());
            assertThat(command.operationKey()).isEqualTo("evt-c");
        });
    }

    @Test
    void ce004_montos_solo_desde_registros_internos() {
        assertThat(recordComponents(RefundRequestCommand.class))
                .containsExactly("reservationId", "trigger", "disputeId", "operationKey");
        assertThat(recordComponents(SettlementCommand.class))
                .containsExactly("reservationId", "trigger", "scope", "disputeId", "operationKey");
    }

    @Test
    void ce005_concurrencia_idempotente() {
        service.handle(command(RESERVATION, DisputeStatus.REJECTED, "evt-1"));
        service.handle(command(RESERVATION, DisputeStatus.REJECTED, "evt-1"));
        assertThat(refund.commands()).hasSize(1);

        service.handle(command(RESERVATION, DisputeStatus.REJECTED, "evt-2"));
        assertThat(refund.commands()).hasSize(1);

        service.handle(command(RESERVATION, DisputeStatus.COMPLETED, "evt-3"));
        assertThat(settlement.commands()).isEmpty();
        assertThat(failures.failures()).isNotEmpty();

        service.handle(command(RESERVATION, DisputeStatus.PENDING, "evt-4"));
        assertThat(refund.commands()).hasSize(1);
        assertThat(settlement.commands()).isEmpty();
        assertThat(eventLog.events()).hasSize(4).allSatisfy(event -> assertThat(event.processedAt()).isNotNull());
    }

    @Test
    void ce006_sin_subresultados_parciales() {
        for (String status : List.of("PARTIALLY_RELEASED", "PARTIALLY_RETAINED", "LIBERAR_DEPOSITO",
                "RETENER_DEPOSITO")) {
            assertThatThrownBy(() -> mapper.toCommand(message(status)))
                    .isInstanceOf(DisputeNotificationValidationException.class);
        }

        assertThat(refund.commands()).isEmpty();
        assertThat(settlement.commands()).isEmpty();
    }

    private DisputeNotificationCommand command(ReservationId reservationId, DisputeStatus status, String eventKey) {
        return mapper.toCommand(new DisputeNotificationMessage(reservationId.value(), DISPUTE.value(), status.name(),
                STATUS_CHANGED_AT, eventKey));
    }

    private static DisputeNotificationMessage message(DisputeStatus status) {
        return message(status.name());
    }

    private static DisputeNotificationMessage message(String status) {
        return new DisputeNotificationMessage(RESERVATION.value(), DISPUTE.value(), status, STATUS_CHANGED_AT,
                "v1.0-4a5b6c");
    }

    private static List<String> recordComponents(Class<?> recordClass) {
        return Arrays.stream(recordClass.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    private static final class RecordingRefundUseCase implements RequestRefundUseCase {

        private final List<RefundRequestCommand> commands = new ArrayList<>();
        private final InMemoryDepositDispositionRepository deposit;

        private RecordingRefundUseCase(InMemoryDepositDispositionRepository deposit) {
            this.deposit = deposit;
        }

        @Override
        public void request(RefundRequestCommand command) {
            commands.add(command);
            applyDisposition(new ReservationId(command.reservationId()), command.disputeId(),
                    DepositDispositionStatus.REFUND_REQUESTED);
        }

        List<RefundRequestCommand> commands() {
            return List.copyOf(commands);
        }

        private void applyDisposition(ReservationId reservationId, UUID disputeId, DepositDispositionStatus target) {
            deposit.findByReservationId(reservationId).orElseGet(() -> {
                DepositDisposition held = DepositDisposition.create(reservationId, NOW, disputeId, null,
                        DepositDispositionStatus.HELD);
                deposit.save(held);
                return held;
            });
            deposit.compareAndSet(reservationId, DepositDispositionStatus.HELD,
                    DepositDisposition.create(reservationId, NOW, disputeId, null, target));
        }
    }

    private static final class RecordingSettlementUseCase implements RequestSettlementUseCase {

        private final List<SettlementCommand> commands = new ArrayList<>();
        private final InMemoryDepositDispositionRepository deposit;

        private RecordingSettlementUseCase(InMemoryDepositDispositionRepository deposit) {
            this.deposit = deposit;
        }

        @Override
        public SettlementRequestResult request(SettlementCommand command) {
            commands.add(command);
            deposit.findByReservationId(command.reservationId()).orElseGet(() -> {
                DepositDisposition held = DepositDisposition.create(command.reservationId(), NOW, command.disputeId(),
                        null, DepositDispositionStatus.HELD);
                deposit.save(held);
                return held;
            });
            deposit.compareAndSet(command.reservationId(), DepositDispositionStatus.HELD,
                    DepositDisposition.create(command.reservationId(), NOW, command.disputeId(), null,
                            DepositDispositionStatus.SETTLEMENT_REQUESTED));
            return new SettlementRequestResult(UUID.randomUUID(), SettlementStatus.PENDING_SEND);
        }

        List<SettlementCommand> commands() {
            return List.copyOf(commands);
        }
    }
}
