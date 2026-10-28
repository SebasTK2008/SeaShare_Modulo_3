package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.BaseRateResult;
import com.seashare.seasharem3.application.dto.ProvideBaseRateCommand;
import com.seashare.seasharem3.application.port.in.ProvideBaseRateUseCase;
import com.seashare.seasharem3.domain.exception.BaseRateException;
import com.seashare.seasharem3.domain.exception.BaseRateException.BaseRateFailureReason;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.ReservationInfoListener;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto.ReservationInfoMessage;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto.ReservationInfoMessageMapper;
import com.seashare.seasharem3.infrastructure.config.amqp.AmqpListenerFailurePolicy;
import com.seashare.seasharem3.support.InMemoryFailureRecorder;
import com.seashare.seasharem3.support.InMemoryReservationInformationRepository;
import com.seashare.seasharem3.support.MessagingJsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de aceptación CE-003 de UC03: toda falla permanente queda registrada, no se persiste
 * información incompleta y se conserva la versión válida previa. Las fallas transitorias se
 * propagan para que la política compartida las reintente y las registre al agotarse.
 *
 * <p>Se ejecuta sin Spring ni Docker: servicio y listener reales, repositorio y registrador de
 * fallos en memoria, y un doble del puerto de UC02.</p>
 */
class ReservationInformationAcceptanceTest {

    private static final UUID RESERVATION_UUID = UUID.fromString("b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10");
    private static final UUID BOAT_UUID = UUID.fromString("3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01");
    private static final UUID OWNER_UUID = UUID.fromString("a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d");
    private static final ReservationId RESERVATION_ID = new ReservationId(RESERVATION_UUID);
    private static final BoatId BOAT = new BoatId(BOAT_UUID);
    private static final OwnerId OWNER = new OwnerId(OWNER_UUID);
    private static final LocalDate START = LocalDate.of(2026, 12, 20);
    private static final LocalDate END = LocalDate.of(2026, 12, 22);
    private static final BaseRate BASE_RATE = new BaseRate(new Money(new BigDecimal("437500.0000")));

    private JsonMapper jsonMapper;
    private InMemoryReservationInformationRepository repository;
    private InMemoryFailureRecorder failureRecorder;
    private StubProvideBaseRateUseCase uc02;
    private ReservationInfoListener listener;

    @BeforeEach
    void setUp() {
        jsonMapper = MessagingJsonMapper.create();
        repository = new InMemoryReservationInformationRepository();
        failureRecorder = new InMemoryFailureRecorder();
        uc02 = new StubProvideBaseRateUseCase();
        RegisterReservationInformationService service = new RegisterReservationInformationService(uc02, repository);
        listener = new ReservationInfoListener(service, new ReservationInfoMessageMapper(),
                new AmqpListenerFailurePolicy(failureRecorder), jsonMapper);
        uc02.respondWith(new BaseRateResult(BOAT, BASE_RATE, null));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = BaseRateFailureReason.class,
            names = {"FLEET_UNAVAILABLE", "DYNAMIC_RATE_NOT_CONFIGURED", "PARAMETERS_UNAVAILABLE"})
    void ce003_transientFailuresArePropagatedAndTheValidVersionIsPreserved(BaseRateFailureReason reason) {
        ReservationInformation valid = seedValidVersion();
        uc02.failWith(new BaseRateException(reason, "transient UC02 failure"));

        assertThatThrownBy(() -> listener.onMessage(validMessage(5, 6)))
                .isInstanceOfSatisfying(BaseRateException.class,
                        exception -> assertThat(exception.isTransient()).isTrue());

        assertThat(failureRecorder.failures()).isEmpty();
        assertThat(repository.findByReservationId(RESERVATION_ID)).contains(valid);
    }

    @Test
    void ce003_missingBaseRateIsRecordedAndTheValidVersionIsPreserved() {
        ReservationInformation valid = seedValidVersion();
        uc02.respondWithNoRate();

        assertThatCode(() -> listener.onMessage(validMessage(5, 6))).doesNotThrowAnyException();

        assertFailureRecorded(RESERVATION_UUID);
        assertThat(repository.findByReservationId(RESERVATION_ID)).contains(valid);
    }

    @Test
    void ce003_permanentBaseRateFailureIsRecordedAndTheValidVersionIsPreserved() {
        ReservationInformation valid = seedValidVersion();
        uc02.failWith(new BaseRateException(BaseRateFailureReason.BASE_RATE_NOT_AVAILABLE, "no rate"));

        assertThatCode(() -> listener.onMessage(validMessage(5, 6))).doesNotThrowAnyException();

        assertFailureRecorded(RESERVATION_UUID);
        assertThat(repository.findByReservationId(RESERVATION_ID)).contains(valid);
    }

    @Test
    void ce003_missingOwnerIsRecordedAndTheValidVersionIsPreserved() {
        ReservationInformation valid = seedValidVersion();

        assertThatCode(() -> listener.onMessage(messageWithPayload(
                new ReservationInfoMessage(RESERVATION_UUID, BOAT_UUID, START, END, 5, null, 6))))
                .doesNotThrowAnyException();

        assertFailureRecorded(RESERVATION_UUID);
        assertThat(repository.findByReservationId(RESERVATION_ID)).contains(valid);
    }

    @Test
    void ce003_missingCapacityIsRecordedAndTheValidVersionIsPreserved() {
        ReservationInformation valid = seedValidVersion();

        assertThatCode(() -> listener.onMessage(messageWithPayload(
                new ReservationInfoMessage(RESERVATION_UUID, BOAT_UUID, START, END, 5, OWNER_UUID, null))))
                .doesNotThrowAnyException();

        assertFailureRecorded(RESERVATION_UUID);
        assertThat(repository.findByReservationId(RESERVATION_ID)).contains(valid);
    }

    @Test
    void ce003_validationFailureIsRecordedAndTheValidVersionIsPreserved() {
        ReservationInformation valid = seedValidVersion();

        assertThatCode(() -> listener.onMessage(validMessage(7, 6))).doesNotThrowAnyException();

        assertFailureRecorded(RESERVATION_UUID);
        assertThat(repository.findByReservationId(RESERVATION_ID)).contains(valid);
    }

    @Test
    void ce003_invalidMessageDoesNotCreateAReservation() {
        UUID newReservation = UUID.randomUUID();

        assertThatCode(() -> listener.onMessage(messageWithPayload(
                new ReservationInfoMessage(newReservation, BOAT_UUID, START, END, 2, null, 6))))
                .doesNotThrowAnyException();

        assertFailureRecorded(newReservation);
        assertThat(repository.findByReservationId(new ReservationId(newReservation))).isEmpty();
    }

    @Test
    void ce003_missingBaseRateDoesNotCreateAReservation() {
        UUID newReservation = UUID.randomUUID();
        uc02.respondWithNoRate();

        assertThatCode(() -> listener.onMessage(messageWithPayload(
                new ReservationInfoMessage(newReservation, BOAT_UUID, START, END, 2, OWNER_UUID, 6))))
                .doesNotThrowAnyException();

        assertFailureRecorded(newReservation);
        assertThat(repository.findByReservationId(new ReservationId(newReservation))).isEmpty();
    }

    private ReservationInformation seedValidVersion() {
        listener.onMessage(validMessage(4, 6));
        assertThat(failureRecorder.failures()).isEmpty();
        return repository.findByReservationId(RESERVATION_ID).orElseThrow();
    }

    private void assertFailureRecorded(UUID reservationId) {
        assertThat(failureRecorder.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.useCase()).isEqualTo("UC03");
            assertThat(failure.reservationId()).isEqualTo(reservationId);
            assertThat(failure.reason()).isNotBlank();
        });
    }

    private Message validMessage(int passengers, int maxCapacity) {
        return messageWithPayload(new ReservationInfoMessage(
                RESERVATION_UUID, BOAT_UUID, START, END, passengers, OWNER_UUID, maxCapacity));
    }

    private Message messageWithPayload(ReservationInfoMessage payload) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType("application/json");
        properties.setMessageId(UUID.randomUUID().toString());
        return new Message(jsonMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8), properties);
    }

    private static final class StubProvideBaseRateUseCase implements ProvideBaseRateUseCase {

        private Supplier<List<BaseRateResult>> response = () -> List.of();

        @Override
        public List<BaseRateResult> provideBaseRates(ProvideBaseRateCommand command) {
            return response.get();
        }

        void respondWith(BaseRateResult result) {
            response = () -> List.of(result);
        }

        void respondWithNoRate() {
            response = () -> List.of();
        }

        void failWith(BaseRateException exception) {
            response = () -> {
                throw exception;
            };
        }
    }
}
