package com.seashare.seasharem3.infrastructure.adapter.in.web;

import com.seashare.seasharem3.application.dto.FinancialRecordResult;
import com.seashare.seasharem3.application.dto.FinancialRecordsPageResult;
import com.seashare.seasharem3.application.dto.QueryFinancialRecordsCommand;
import com.seashare.seasharem3.application.exception.ContractException;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.QueryFinancialRecordsUseCase;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.FinancialRecordType;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.ReportScope;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.infrastructure.adapter.in.web.dto.FinancialRecordsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@Tag(name = "UC12 · Registros financieros", description = "Histórico paginado de operaciones financieras confirmadas")
@RestController
@RequestMapping("/api/v1/financial-records")
public class FinancialRecordsController {

    private final QueryFinancialRecordsUseCase useCase;

    public FinancialRecordsController(QueryFinancialRecordsUseCase useCase) {
        this.useCase = useCase;
    }

    @Operation(operationId = "queryFinancialRecords", summary = "Consultar registros financieros",
            description = "Solo lectura de registros confirmados, no de intenciones pendientes o fallidas. "
                    + "Orden estable: created_at DESC y record_id DESC. Si no hay resultados, page=1 devuelve una página vacía. "
                    + "scope y owner_id son datos declarados: no hay autenticación ni verificación de pertenencia. "
                    + "Los filtros se aplican dentro del alcance declarado y no lo amplían.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de registros, posiblemente vacía",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = FinancialRecordsResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: parámetros inválidos, page < 1, size fuera de 1–100, "
                    + "owner_id ausente para OWNER o date_from posterior a date_to",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = "#/components/schemas/ProblemDetails"))),
            @ApiResponse(responseCode = "404", description = "PAGE_OUT_OF_RANGE: page > max(total_pages, 1)",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = "#/components/schemas/ProblemDetails"))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: fallo no previsto",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = "#/components/schemas/ProblemDetails")))
    })
    @GetMapping
    public ResponseEntity<FinancialRecordsResponse> get(
            @Parameter(description = "Alcance declarado, no autenticado", required = true,
                    schema = @Schema(allowableValues = {"OWNER", "PLATFORM"}), example = "PLATFORM")
            @RequestParam String scope,
            @Parameter(description = "Número de página, desde 1", required = true, schema = @Schema(minimum = "1"), example = "1")
            @RequestParam int page,
            @Parameter(description = "Tamaño de página (10 si se omite)", schema = @Schema(minimum = "1", maximum = "100", defaultValue = "10"))
            @RequestParam(required = false) Integer size,
            @Parameter(description = "Filtro por tipo de registro confirmado",
                    schema = @Schema(allowableValues = {"CHARGE", "REFUND", "SETTLEMENT", "COMMISSION"}))
            @RequestParam(name = "transaction_type", required = false) String transactionType,
            @Parameter(description = "Obligatorio si scope=OWNER; filtro opcional si scope=PLATFORM. No valida identidad",
                    schema = @Schema(type = "string", format = "uuid"), example = "a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d")
            @RequestParam(name = "owner_id", required = false) String ownerId,
            @Parameter(description = "Filtro por embarcación", schema = @Schema(type = "string", format = "uuid"),
                    example = "3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01")
            @RequestParam(name = "boat_id", required = false) String boatId,
            @Parameter(description = "Filtro por reserva", schema = @Schema(type = "string", format = "uuid"),
                    example = "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10")
            @RequestParam(name = "reservation_id", required = false) String reservationId,
            @Parameter(description = "Inicio del rango de fechas; no posterior a date_to",
                    schema = @Schema(type = "string", format = "date"), example = "2026-12-01")
            @RequestParam(name = "date_from", required = false) String dateFrom,
            @Parameter(description = "Fin del rango de fechas", schema = @Schema(type = "string", format = "date"), example = "2026-12-31")
            @RequestParam(name = "date_to", required = false) String dateTo) {
        QueryFinancialRecordsCommand command = new QueryFinancialRecordsCommand(
                parseEnum(scope, ReportScope.class, "scope"),
                page,
                size,
                parseEnum(transactionType, FinancialRecordType.class, "transaction_type"),
                parseId(ownerId, "owner_id", OwnerId.class),
                parseId(boatId, "boat_id", BoatId.class),
                parseId(reservationId, "reservation_id", ReservationId.class),
                parseDate(dateFrom, "date_from"),
                parseDate(dateTo, "date_to"));
        return ResponseEntity.ok(toResponse(useCase.query(command)));
    }

    private static FinancialRecordsResponse toResponse(FinancialRecordsPageResult result) {
        List<FinancialRecordsResponse.FinancialRecordResponse> content = result.content().stream()
                .map(FinancialRecordsController::toResponse)
                .toList();
        return new FinancialRecordsResponse(content, result.pageNumber(), result.pageSize(),
                result.totalElements(), result.totalPages());
    }

    private static FinancialRecordsResponse.FinancialRecordResponse toResponse(FinancialRecordResult result) {
        return new FinancialRecordsResponse.FinancialRecordResponse(
                result.recordType().name(),
                result.reservationId().value().toString(),
                result.ownerId().value().toString(),
                result.boatId().value().toString(),
                result.amount().toOutputAmount().toPlainString(),
                result.createdAt().toString(),
                result.externalReference());
    }

    private static <E extends Enum<E>> E parseEnum(String value, Class<E> type, String field) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw validation(field, "Unsupported value.");
        }
    }

    private static LocalDate parseDate(String value, String field) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw validation(field, "Date must use the yyyy-MM-dd format.");
        }
    }

    private static <T> T parseId(String value, String field, Class<T> type) {
        if (value == null) {
            return null;
        }
        try {
            UUID uuid = UUID.fromString(value);
            if (type == OwnerId.class) return type.cast(new OwnerId(uuid));
            if (type == BoatId.class) return type.cast(new BoatId(uuid));
            return type.cast(new ReservationId(uuid));
        } catch (IllegalArgumentException exception) {
            throw validation(field, "Value must be a UUID.");
        }
    }

    private static ContractException validation(String field, String detail) {
        return new ContractException(ErrorCode.VALIDATION_ERROR, detail,
                List.of(new com.seashare.seasharem3.application.exception.ApiFieldError(field, detail)));
    }
}
