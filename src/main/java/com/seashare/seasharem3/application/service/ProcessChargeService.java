package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.ChargeCommand;
import com.seashare.seasharem3.application.dto.ProcessChargeCommand;
import com.seashare.seasharem3.application.port.in.ProcessChargeUseCase;
import com.seashare.seasharem3.application.port.out.ChargeCommandPublisher;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.application.port.out.ReservationInformationRepository;
import com.seashare.seasharem3.domain.exception.InvalidPaymentTokenException;
import com.seashare.seasharem3.domain.exception.ReservationValueNotCalculatedException;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.PaymentMethodReference;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta UC05/HU1: valida la solicitud de cobro, recupera el valor total calculado por UC04,
 * registra la intención de cobro y publica el comando en la cola interna.
 *
 * <p>La intención y el mensaje del outbox se confirman en la <em>misma</em> transacción: o se
 * registra el intento y se encola el comando, o no ocurre nada (general-plan D-08; UC05 plan regla 4).
 * No se contacta a la pasarela desde aquí: el envío lo hace el worker de la cola interna.</p>
 *
 * <p>El monto cobrado es un <em>único</em> importe, el valor total registrado por UC04 (alquiler +
 * seguro + depósito); UC05 no recalcula ni descompone el monto (RF-002, RF-015). La intención se
 * identifica por la clave idempotente del intento ({@code reservation_id} + {@code status} +
 * {@code status_changed_at}), de modo que una invocación repetida no crea un segundo intento ni
 * cobra dos veces (RF-004A, general-plan D-33).</p>
 *
 * <p>Solo se abre un intento si la reserva no tiene ya un cobro capturado: una reserva cobrada no
 * vuelve a cobrarse (D-33). Un token ausente impide el cobro y se rechaza con
 * {@link InvalidPaymentTokenException}, que UC07 captura y registra en {@code operational_failure}
 * (RF-013, D-UC05-07). La completitud del resto de la solicitud a la pasarela la verifica el
 * worker, que marca la intención como rechazada sin reintentar cuando falta un dato obligatorio del
 * contrato de cobro.</p>
 *
 * <p>Fuente: UC05 plan T007, T008 y reglas 1–4; SPEC RF-001, RF-002, RF-004, RF-004A, RF-009,
 * RF-013 y RF-015.</p>
 */
public class ProcessChargeService implements ProcessChargeUseCase {

    /** Metadato no sensible con el correo del pagador; lo exige el contrato de cobro. [CONV] */
    private static final String PAYER_EMAIL_KEY = "payer_email";

    /** Metadato no sensible con los últimos cuatro dígitos del medio de pago. [CONV] RF-012. */
    private static final String LAST_FOUR_KEY = "last_four";

    private final ReservationInformationRepository reservationInformationRepository;
    private final ChargeIntentRepository chargeIntentRepository;
    private final ChargeCommandPublisher chargeCommandPublisher;
    private final Clock instantClock;

    public ProcessChargeService(ReservationInformationRepository reservationInformationRepository,
            ChargeIntentRepository chargeIntentRepository, ChargeCommandPublisher chargeCommandPublisher,
            Clock instantClock) {
        this.reservationInformationRepository = Objects.requireNonNull(reservationInformationRepository,
                "reservationInformationRepository");
        this.chargeIntentRepository = Objects.requireNonNull(chargeIntentRepository, "chargeIntentRepository");
        this.chargeCommandPublisher = Objects.requireNonNull(chargeCommandPublisher, "chargeCommandPublisher");
        this.instantClock = Objects.requireNonNull(instantClock, "instantClock");
    }

    @Override
    @Transactional
    public void process(ProcessChargeCommand command) {
        Objects.requireNonNull(command, "command");
        UUID reservationId = Objects.requireNonNull(command.reservationId(), "command.reservationId");
        String paymentTokenRef = requirePaymentToken(command.paymentTokenRef());
        IdempotencyKey idempotencyKey = new IdempotencyKey(
                Objects.requireNonNull(command.operationKey(), "command.operationKey"));
        ReservationId reservation = new ReservationId(reservationId);

        if (chargeIntentRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            return;
        }
        if (chargeIntentRepository.findApprovedByReservationId(reservation).isPresent()) {
            return;
        }

        ReservationInformation information = reservationInformationRepository.findByReservationId(reservation)
                .orElseThrow(() -> new ReservationValueNotCalculatedException(
                        "There is no registered information for reservation " + reservationId + "."));
        if (!information.isCalculated()) {
            throw new ReservationValueNotCalculatedException(
                    "There is no calculated total amount for reservation " + reservationId + ".");
        }

        ChargeIntent intent = ChargeIntent.create(UUID.randomUUID(), reservation, idempotencyKey,
                information.totalAmount(),
                new PaymentMethodReference(paymentTokenRef, command.paymentMethodType(),
                        lastFour(command.paymentMetadata())),
                instantClock.instant());

        chargeIntentRepository.save(intent);
        chargeCommandPublisher.publish(new ChargeCommand(intent.id(), payerEmail(command.paymentMetadata())));
    }

    /** El token del medio de pago es obligatorio: sin él no hay cobro posible (RF-001, RF-013). */
    private static String requirePaymentToken(String paymentTokenRef) {
        if (paymentTokenRef == null || paymentTokenRef.isBlank()) {
            throw new InvalidPaymentTokenException(
                    "The payment token is required to process a charge.");
        }
        return paymentTokenRef;
    }

    /** Correo del pagador; dato plano que el contrato de cobro exige en {@code payer.email}. */
    private static String payerEmail(Map<String, String> paymentMetadata) {
        return paymentMetadata == null ? null : paymentMetadata.get(PAYER_EMAIL_KEY);
    }

    /** Últimos cuatro dígitos del medio de pago; metadato no sensible y opcional (RF-012). */
    private static String lastFour(Map<String, String> paymentMetadata) {
        return paymentMetadata == null ? null : paymentMetadata.get(LAST_FOUR_KEY);
    }
}
