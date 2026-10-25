package com.seashare.seasharem3.infrastructure.adapter.in.web;

import com.seashare.seasharem3.application.dto.FinancialParametersCommand;
import com.seashare.seasharem3.application.dto.FinancialParametersResult;
import com.seashare.seasharem3.application.port.in.LoadFinancialParametersUseCase;
import com.seashare.seasharem3.application.port.in.SaveFinancialParametersUseCase;
import com.seashare.seasharem3.infrastructure.adapter.in.web.dto.FinancialParametersRequest;
import com.seashare.seasharem3.infrastructure.adapter.in.web.dto.FinancialParametersResponse;
import com.seashare.seasharem3.infrastructure.adapter.in.web.dto.SaveFinancialParametersResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador de entrada REST de UC11: expone {@code GET} y {@code PUT /api/v1/financial-parameters}.
 * Solo invoca {@link LoadFinancialParametersUseCase} y {@link SaveFinancialParametersUseCase}
 * (puertos de entrada) y nunca accede a repositorios ni a servicios directamente
 * [general-plan §3.4-3/§3.4-5; UC11 plan T018/T032].
 *
 * <p>Sin autenticación ni autorización: el Administrador Financiero es el usuario esperado; la
 * seguridad está fuera del alcance del proyecto (general-plan §7.1, D-21) [SPEC RF-008].
 *
 * <p>Los errores (400, 404, 500) los traduce el {@code GlobalExceptionHandler} a Problem Details
 * (RFC 9457) según el catálogo de {@code contracts/README §3.3–§3.4} [CONV].
 *
 * <p>Fuente: UC11 plan T018/T032; contratos {@code rest/UC11-obtener-parametros-financieros.md}
 * y {@code rest/UC11-guardar-parametros-financieros.md} [CONV].
 */
@RestController
@RequestMapping("/api/v1/financial-parameters")
public class FinancialParametersController {

    private final LoadFinancialParametersUseCase loadUseCase;
    private final SaveFinancialParametersUseCase saveUseCase;

    public FinancialParametersController(LoadFinancialParametersUseCase loadUseCase,
                                         SaveFinancialParametersUseCase saveUseCase) {
        this.loadUseCase = loadUseCase;
        this.saveUseCase = saveUseCase;
    }

    /**
     * Devuelve la versión vigente de los parámetros financieros globales junto con los datos derivados
     * de solo lectura (regla del depósito y ventanas de temporada alta).
     *
     * <ul>
     *   <li>{@code 200 OK} — parámetros vigentes [SPEC HU3 esc. 1, RF-009].</li>
     *   <li>{@code 404 PARAMETERS_NOT_CONFIGURED} — no existe ninguna versión [SPEC HU3 esc. 2].</li>
     *   <li>{@code 500 INTERNAL_ERROR} — error no controlado [CONV].</li>
     * </ul>
     */
    @GetMapping
    public ResponseEntity<FinancialParametersResponse> getFinancialParameters() {
        FinancialParametersResult result = loadUseCase.load();
        return ResponseEntity.ok(toResponse(result));
    }

    /**
     * Guarda los cuatro parámetros financieros globales como una nueva versión vigente (append-only).
     * Si los cuatro valores son idénticos a la versión vigente, devuelve la versión vigente sin crear
     * una nueva (idempotente) [SPEC D-UC11-03].
     *
     * <ul>
     *   <li>{@code 200 OK} — {@code { "version_number": N }} [SPEC HU1, HU2, HU4 esc. 1].</li>
     *   <li>{@code 400 VALIDATION_ERROR} — campo inválido, fuera de rango o ausente [SPEC RF-013].</li>
     *   <li>{@code 500 PARAMETERS_SAVE_FAILED} — fallo de persistencia [SPEC HU4 esc. 3].</li>
     * </ul>
     */
    @PutMapping
    public ResponseEntity<SaveFinancialParametersResponse> saveFinancialParameters(
            @Valid @RequestBody FinancialParametersRequest request) {

        request.insuranceFeePerPassenger().requireNonNegative();

        FinancialParametersCommand command = new FinancialParametersCommand(
                request.platformCommissionPercentage(),
                request.insuranceFeePerPassenger(),
                request.weekendIncreasePercentage(),
                request.highSeasonIncreasePercentage());

        FinancialParametersResult result = saveUseCase.save(command);
        return ResponseEntity.ok(new SaveFinancialParametersResponse(result.versionNumber()));
    }

    // --- mapper interno (sin MapStruct; el mapeo es trivial) ---

    private static FinancialParametersResponse toResponse(FinancialParametersResult result) {
        return new FinancialParametersResponse(
                result.versionNumber(),
                result.commissionPct(),
                result.insuranceFeePerPassenger(),
                result.weekendIncreasePct(),
                result.highSeasonIncreasePct(),
                result.guaranteeDepositRule(),
                result.highSeasonWindows());
    }
}

