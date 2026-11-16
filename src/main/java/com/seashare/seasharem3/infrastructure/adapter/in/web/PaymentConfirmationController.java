package com.seashare.seasharem3.infrastructure.adapter.in.web;

import com.seashare.seasharem3.application.dto.PaymentConfirmationQuery;
import com.seashare.seasharem3.application.dto.PaymentConfirmationResult;
import com.seashare.seasharem3.application.port.in.GetPaymentConfirmationUseCase;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.infrastructure.adapter.in.web.dto.PaymentConfirmationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "UC06 · Confirmación de pago", description = "Consulta del estado persistido del cobro de una reserva")
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
    @Operation(operationId = "getPaymentConfirmation", summary = "Consultar confirmación de pago",
            description = "Sin cuerpo y de solo lectura. Consulta la intención vigente persistida, sin llamar a Mercado Pago. "
                    + "Los montos no disponibles se devuelven como null, no como cero. Un reembolso confirmado puede aportar released_amount.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado actual y datos disponibles del cobro",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentConfirmationResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: identificador inválido",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = "#/components/schemas/ProblemDetails"))),
            @ApiResponse(responseCode = "404", description = "CHARGE_INTENT_NOT_FOUND: no existe intención de cobro para la reserva",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = "#/components/schemas/ProblemDetails"))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: fallo no previsto",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = "#/components/schemas/ProblemDetails")))
    })
    @GetMapping("/{reservationId}/payment-confirmation")
    public ResponseEntity<PaymentConfirmationResponse> get(
            @Parameter(description = "Identificador UUID de la reserva", required = true,
                    schema = @Schema(type = "string", format = "uuid"), example = "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10")
            @PathVariable UUID reservationId) {
        PaymentConfirmationResult result = getPaymentConfirmationUseCase.get(
                new PaymentConfirmationQuery(new ReservationId(reservationId)));

        return ResponseEntity.ok(new PaymentConfirmationResponse(result.reservationId(), result.status(),
                result.detail(), result.authorizedAmount(), result.capturedAmount(), result.releasedAmount(),
                result.chargedAmount(), result.externalReference()));
    }
}
