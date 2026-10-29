package com.seashare.seasharem3.infrastructure.adapter.in.web;

import com.seashare.seasharem3.application.dto.CalculateReservationValueCommand;
import com.seashare.seasharem3.application.dto.ReservationValueResult;
import com.seashare.seasharem3.application.port.in.CalculateReservationValueUseCase;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.infrastructure.adapter.in.web.dto.ReservationValueResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

/**
 * Adaptador de entrada REST de UC04: {@code POST /api/v1/reservations/{reservation_id}/calculated-value}
 * sin cuerpo [contrato UC04 §2; SPEC RF-001]. Solo invoca {@link CalculateReservationValueUseCase}
 * (puerto de entrada) y nunca accede a repositorios [general-plan §3.4-3/-5].
 *
 * <p>Un identificador no válido es {@code 400 VALIDATION_ERROR} (E2) y las excepciones de dominio las
 * traduce el {@code GlobalExceptionHandler} compartido a Problem Details (404/422/503/500).</p>
 *
 * <p>Sin autenticación ni autorización: seguridad fuera de alcance (general-plan §7.1, D-21) [SPEC RF-001].</p>
 *
 * Fuente: UC04 plan T012; contrato {@code rest/UC04-valor-calculado-reserva.md} [CONV].
 */
@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationValueController {

    private final CalculateReservationValueUseCase calculateReservationValueUseCase;

    public ReservationValueController(CalculateReservationValueUseCase calculateReservationValueUseCase) {
        this.calculateReservationValueUseCase = Objects.requireNonNull(calculateReservationValueUseCase,
                "calculateReservationValueUseCase");
    }

    /**
     * Calcula y devuelve el desglose de la reserva, y deja los montos registrados para "Procesar
     * cobro" (RF-008, CE-004).
     *
     * <ul>
     *   <li>{@code 200 OK} — desglose completo [SPEC RF-007, contrato §4].</li>
     *   <li>{@code 400 VALIDATION_ERROR} — el identificador no es un UUID [E2].</li>
     *   <li>{@code 404/422/503} — información ausente, incompleta o parámetros sin configurar [E3/E6/E4].</li>
     *   <li>{@code 500 INTERNAL_ERROR} — error no previsto [CONV].</li>
     * </ul>
     */
    @PostMapping("/{reservationId}/calculated-value")
    public ResponseEntity<ReservationValueResponse> calculate(@PathVariable UUID reservationId) {
        ReservationValueResult result = calculateReservationValueUseCase.calculate(
                new CalculateReservationValueCommand(new ReservationId(reservationId)));

        return ResponseEntity.ok(new ReservationValueResponse(result.reservationId(), result.rentalAmount(),
                result.insuranceAmount(), result.guaranteeDepositAmount(), result.totalAmount()));
    }
}
