package com.seashare.seasharem3.infrastructure.adapter.in.web.financialreport;

import com.seashare.seasharem3.application.dto.GetFinancialReportCommand;
import com.seashare.seasharem3.application.exception.ApiFieldError;
import com.seashare.seasharem3.application.exception.ContractException;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.ExportFinancialReportUseCase;
import com.seashare.seasharem3.application.port.in.GetFinancialReportUseCase;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.Periodicity;
import com.seashare.seasharem3.domain.valueobject.ReportPeriod;
import com.seashare.seasharem3.domain.valueobject.ReportScope;
import com.seashare.seasharem3.infrastructure.adapter.in.web.financialreport.dto.FinancialReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "UC13 · Informe financiero", description = "Consulta y exportación de agregados por período y alcance declarado")
@RestController
@RequestMapping("/api/v1/financial-report")
public class FinancialReportController {

    private final GetFinancialReportUseCase getUseCase;
    private final ExportFinancialReportUseCase exportUseCase;
    private final CsvReportExporter exporter;
    private final FinancialReportResponseMapper responseMapper;

    public FinancialReportController(GetFinancialReportUseCase getUseCase,
                                     ExportFinancialReportUseCase exportUseCase,
                                     CsvReportExporter exporter,
                                     FinancialReportResponseMapper responseMapper) {
        this.getUseCase = getUseCase;
        this.exportUseCase = exportUseCase;
        this.exporter = exporter;
        this.responseMapper = responseMapper;
    }

    @Operation(operationId = "getFinancialReport", summary = "Consultar informe financiero",
            description = "Agrega solo registros confirmados. PLATFORM: neto = cobros - reembolsos - liquidaciones; "
                    + "OWNER: ganancia = liquidaciones del propietario. Las comisiones son informativas, no se descuentan otra vez. "
                    + "Si se omite period se usa el último período cerrado. Permite consultar períodos abiertos y compara con el "
                    + "período anterior equivalente. Solo lectura. scope y owner_id son datos declarados, sin validación de identidad.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Informe y comparación temporal",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = FinancialReportResponse.class))),
            @ApiResponse(responseCode = "400", description = "INVALID_PERIOD: periodicidad o período inválido; "
                    + "VALIDATION_ERROR: parámetros ausentes/incorrectos u owner_id requerido para OWNER",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = "#/components/schemas/ProblemDetails"))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: fallo no previsto",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = "#/components/schemas/ProblemDetails")))
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FinancialReportResponse> get(
            @Parameter(description = "Alcance declarado, no autenticado", required = true,
                    schema = @Schema(allowableValues = {"OWNER", "PLATFORM"}), example = "PLATFORM")
            @RequestParam String scope,
            @Parameter(description = "Periodicidad del informe", required = true,
                    schema = @Schema(allowableValues = {"SEMI_MONTHLY", "MONTHLY", "QUARTERLY"}), example = "MONTHLY")
            @RequestParam String periodicity,
            @Parameter(description = "Obligatorio si scope=OWNER; identidad declarada sin verificar pertenencia",
                    schema = @Schema(type = "string", format = "uuid"), example = "a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d")
            @RequestParam(name = "owner_id", required = false) String ownerId,
            @Parameter(description = "SEMI_MONTHLY: YYYY-MM-H1/H2; MONTHLY: YYYY-MM; QUARTERLY: YYYY-Q1..Q4. "
                    + "Debe coincidir con periodicity. Si se omite, usa el último período cerrado",
                    examples = {@ExampleObject(name = "Mensual", value = "2026-10"),
                            @ExampleObject(name = "Quincenal", value = "2026-10-H1"),
                            @ExampleObject(name = "Trimestral", value = "2026-Q4")})
            @RequestParam(required = false) String period) {
        return ResponseEntity.ok(responseMapper.toResponse(getUseCase.get(
                command(scope, periodicity, ownerId, period))));
    }

    @Operation(operationId = "exportFinancialReport", summary = "Descargar informe financiero en CSV",
            description = "Mismos agregados, alcance y comparación que la consulta JSON, pero exige period explícito. "
                    + "Descarga de solo lectura, delimitada por comas, con cabecera y fecha de generación. "
                    + "La variación porcentual queda vacía si el valor anterior es cero. "
                    + "Use Accept: text/csv para éxito; los errores se representan como application/problem+json.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Archivo CSV del período solicitado",
                    headers = @Header(name = "Content-Disposition", description = "Descarga como archivo adjunto",
                            schema = @Schema(type = "string", example = "attachment; filename=\"informe_financiero_2026-10.csv\"")),
                    content = @Content(mediaType = "text/csv", schema = @Schema(type = "string"),
                            examples = @ExampleObject(name = "Informe mensual", value = """
                                    Fecha de Generacion,Alcance,Periodicidad,Periodo,Neto Generado,Total Cobros,Total Reembolsos,Total Dispersiones,Total Comisiones,Count Charge,Count Refund,Count Settlement,Count Commission,Periodo Anterior,Variacion Absoluta,Variacion Porcentual
                                    2026-11-01T10:00:00Z,PLATFORM,MONTHLY,2026-10,150000.00,1150000.00,100000.00,900000.00,135000.00,1,1,1,1,2026-09,20000.00,15.38
                                    """))),
            @ApiResponse(responseCode = "400", description = "INVALID_PERIOD: periodicidad o período inválido; "
                    + "VALIDATION_ERROR: parámetros ausentes/incorrectos u owner_id requerido para OWNER",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = "#/components/schemas/ProblemDetails"))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: fallo al generar el CSV",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = "#/components/schemas/ProblemDetails")))
    })
    @GetMapping(path = "/export", produces = "text/csv")
    public ResponseEntity<String> export(
            @Parameter(description = "Alcance declarado, no autenticado", required = true,
                    schema = @Schema(allowableValues = {"OWNER", "PLATFORM"}), example = "PLATFORM")
            @RequestParam String scope,
            @Parameter(description = "Periodicidad del informe", required = true,
                    schema = @Schema(allowableValues = {"SEMI_MONTHLY", "MONTHLY", "QUARTERLY"}), example = "MONTHLY")
            @RequestParam String periodicity,
            @Parameter(description = "Obligatorio si scope=OWNER; identidad declarada sin verificar pertenencia",
                    schema = @Schema(type = "string", format = "uuid"), example = "a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d")
            @RequestParam(name = "owner_id", required = false) String ownerId,
            @Parameter(description = "Período explícito obligatorio. SEMI_MONTHLY: YYYY-MM-H1/H2; MONTHLY: YYYY-MM; "
                    + "QUARTERLY: YYYY-Q1..Q4. Debe coincidir con periodicity", required = true,
                    examples = {@ExampleObject(name = "Mensual", value = "2026-10"),
                            @ExampleObject(name = "Quincenal", value = "2026-10-H1"),
                            @ExampleObject(name = "Trimestral", value = "2026-Q4")})
            @RequestParam String period) {
        var result = exportUseCase.export(command(scope, periodicity, ownerId, period));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("informe_financiero_" + safeFilenamePeriod(result.report().period().value()) + ".csv").build());
        return ResponseEntity.ok().headers(headers).body(exporter.export(result));
    }

    private static String safeFilenamePeriod(String period) {
        return period.replaceAll("[^0-9A-Za-z-]", "_");
    }

    private static GetFinancialReportCommand command(String scope, String periodicity,
                                                      String ownerId, String period) {
        Periodicity parsedPeriodicity = parsePeriodicity(periodicity);
        return new GetFinancialReportCommand(parsedPeriodicity,
                parseEnum(scope, ReportScope.class, "scope"), parseOwner(ownerId),
                period == null ? null : new ReportPeriod(parsedPeriodicity, period));
    }

    private static OwnerId parseOwner(String value) {
        if (value == null) return null;
        try {
            return new OwnerId(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            throw validation("owner_id", "Value must be a UUID.");
        }
    }

    private static <E extends Enum<E>> E parseEnum(String value, Class<E> type, String field) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw validation(field, "Unsupported value.");
        }
    }

    private static Periodicity parsePeriodicity(String value) {
        try {
            return Enum.valueOf(Periodicity.class, value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ContractException(ErrorCode.INVALID_PERIOD, "Unsupported periodicity.",
                    List.of(new ApiFieldError("periodicity", "Unsupported value.")));
        }
    }

    private static ContractException validation(String field, String message) {
        return new ContractException(ErrorCode.VALIDATION_ERROR, message,
                List.of(new ApiFieldError(field, message)));
    }
}
