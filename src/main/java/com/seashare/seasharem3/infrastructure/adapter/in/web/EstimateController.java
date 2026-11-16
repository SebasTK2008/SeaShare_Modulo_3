package com.seashare.seasharem3.infrastructure.adapter.in.web;

import com.seashare.seasharem3.application.dto.BatchEstimateResult;
import com.seashare.seasharem3.application.dto.EstimateBatchCommand;
import com.seashare.seasharem3.application.dto.EstimateSingleCommand;
import com.seashare.seasharem3.application.dto.SingleEstimateResult;
import com.seashare.seasharem3.application.port.in.EstimateBatchUseCase;
import com.seashare.seasharem3.application.port.in.EstimateSingleUseCase;
import com.seashare.seasharem3.infrastructure.adapter.in.web.dto.BatchEstimateRequest;
import com.seashare.seasharem3.infrastructure.adapter.in.web.dto.BatchEstimateResponse;
import com.seashare.seasharem3.infrastructure.adapter.in.web.dto.SingleEstimateRequest;
import com.seashare.seasharem3.infrastructure.adapter.in.web.dto.SingleEstimateResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "UC01 · Estimaciones", description = "Estimaciones informativas para Reservas; no persisten ni bloquean activos")
@RestController
@RequestMapping("/api/v1/estimates")
public class EstimateController {

    private final EstimateBatchUseCase batchUseCase;
    private final EstimateSingleUseCase singleUseCase;

    public EstimateController(EstimateBatchUseCase batchUseCase,
                              EstimateSingleUseCase singleUseCase) {
        this.batchUseCase = batchUseCase;
        this.singleUseCase = singleUseCase;
    }

    @Operation(operationId = "estimateBatch", summary = "Estimar alquiler de un lote de embarcaciones",
            description = "Solo acepta boat_ids; lista vacía devuelve 200 con arreglos vacíos. Deduplica IDs y usa "
                    + "la fecha actual, 1 día y 1 pasajero. Incluye seguro, no depósito de garantía. "
                    + "Los IDs sin tarifa se devuelven en unavailable con NO_BASE_RATE, no como error 422. "
                    + "Máximo configurable (seashare.estimates.max-batch-size, por defecto 50). Solo lectura.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estimaciones y embarcaciones no disponibles",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BatchEstimateResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BatchEstimateBadRequest"),
            @ApiResponse(responseCode = "503", ref = "#/components/responses/BatchEstimateUnavailable"),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    })
    @PostMapping("/batch")
    public ResponseEntity<BatchEstimateResponse> estimateBatch(
            @Valid @RequestBody BatchEstimateRequest request) {
        BatchEstimateResult result = batchUseCase.estimate(
                new EstimateBatchCommand(request.boatIds()));
        return ResponseEntity.ok(toBatchResponse(result));
    }

    @Operation(operationId = "estimateIndividual", summary = "Estimar alquiler para fechas y pasajeros",
            description = "La tarifa dinámica de start_date se aplica a todos los días inclusivos. "
                    + "Inicio y fin iguales equivalen a 1 día. Incluye seguro, no depósito ni ajustes posteriores. "
                    + "warning es obligatorio y debe mostrarse tal como lo devuelve el sistema. Solo lectura.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estimación con advertencia obligatoria",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SingleEstimateResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/IndividualEstimateBadRequest"),
            @ApiResponse(responseCode = "422", ref = "#/components/responses/BaseRateNotAvailable"),
            @ApiResponse(responseCode = "503", ref = "#/components/responses/IndividualEstimateUnavailable"),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    })
    @PostMapping("/individual")
    public ResponseEntity<SingleEstimateResponse> estimateIndividual(
            @Valid @RequestBody SingleEstimateRequest request) {
        SingleEstimateResult result = singleUseCase.estimate(
                new EstimateSingleCommand(
                        request.boatId(),
                        request.startDate(),
                        request.endDate(),
                        request.passengers()));
        return ResponseEntity.ok(toSingleResponse(result));
    }

    private static BatchEstimateResponse toBatchResponse(BatchEstimateResult r) {
        return new BatchEstimateResponse(
                r.evaluationDate(),
                r.durationDays(),
                r.passengers(),
                r.estimates().stream()
                        .map(e -> new BatchEstimateResponse.EstimateItem(e.boatId(), e.estimatedTotal()))
                        .toList(),
                r.unavailable().stream()
                        .map(u -> new BatchEstimateResponse.UnavailableItem(u.boatId(), u.reason()))
                        .toList());
    }

    private static SingleEstimateResponse toSingleResponse(SingleEstimateResult r) {
        return new SingleEstimateResponse(
                r.boatId(),
                r.startDate(),
                r.endDate(),
                r.durationDays(),
                r.passengers(),
                r.estimatedTotal(),
                r.warning());
    }
}
