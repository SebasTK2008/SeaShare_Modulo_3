package com.seashare.seasharem3.infrastructure.adapter.in.web;

import com.seashare.seasharem3.application.dto.PaymentConfirmationQuery;
import com.seashare.seasharem3.application.dto.PaymentConfirmationResult;
import com.seashare.seasharem3.application.port.in.GetPaymentConfirmationUseCase;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.infrastructure.adapter.in.web.dto.PaymentConfirmationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

/**
 * Adaptador de entrada REST de UC06:
 * {@code GET /api/v1/reservations/{reservation_id}/payment-confirmation}.
 * Solo invoca {@link GetPaymentConfirmationUseCase} y nunca accede a repositorios
 * [general-plan §3.4-3/-5].
 *
 * <p>Un identificador no válido es {@code 400 VALIDATION_ERROR} y las excepciones de dominio las
 * traduce el {@code GlobalExceptionHandler} compartido a Problem Details (404/500).</p>
 *
 * <p>Sin autenticación ni autorización: seguridad fuera de alcance (general-plan §7.1, D-21).</p>
 *
 * <p>Fuente: UC06 plan T006; contrato {@code rest/UC06-confirmacion-pago.md} §§2–5.</p>
 */
@RestController
@RequestMapping("/api/v1/reservations")
public class PaymentConfirmationController {

    private final GetPaymentConfirmationUseCase getPaymentConfirmationUseCase;

    public PaymentConfirmationController(GetPaymentConfirmationUseCase getPaymentConfirmationUseCase) {
        this.getPaymentConfirmationUseCase = Objects.requireNonNull(getPaymentConfirmationUseCase,
                "getPaymentConfirmationUseCase");
    }

    /**
     * Devuelve el estado vigente del cobro y los datos disponibles de la intención.
     *
     * <ul>
     *   <li>{@code 200 OK} — confirmación de pago [SPEC RF-003, contrato §4].</li>
     *   <li>{@code 400 VALIDATION_ERROR} — el identificador no es un UUID [E2].</li>
     *   <li>{@code 404 CHARGE_INTENT_NOT_FOUND} — no existe intención para la reserva [E3].</li>
     *   <li>{@code 500 INTERNAL_ERROR} — error no previsto [CONV].</li>
     * </ul>
     */
    @GetMapping("/{reservationId}/payment-confirmation")
    public ResponseEntity<PaymentConfirmationResponse> get(@PathVariable UUID reservationId) {
        PaymentConfirmationResult result = getPaymentConfirmationUseCase.get(
                new PaymentConfirmationQuery(new ReservationId(reservationId)));

        return ResponseEntity.ok(new PaymentConfirmationResponse(result.reservationId(), result.status(),
                result.detail(), result.authorizedAmount(), result.capturedAmount(), result.releasedAmount(),
                result.chargedAmount(), result.externalReference()));
    }
}
