package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.FinancialParametersCommand;
import com.seashare.seasharem3.application.dto.FinancialParametersResult;
import com.seashare.seasharem3.application.exception.ContractException;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.LoadFinancialParametersUseCase;
import com.seashare.seasharem3.application.port.in.SaveFinancialParametersUseCase;
import com.seashare.seasharem3.application.port.out.ClockPort;
import com.seashare.seasharem3.application.port.out.FinancialParametersRepository;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.service.HighSeasonCalendar;
import com.seashare.seasharem3.domain.valueobject.HighSeasonWindow;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio de aplicación de UC11: guarda y carga los parámetros financieros globales.
 * Fuente: UC11 plan T017 (save) y T031 (load); SPEC RF-005, RF-007, RF-009, RF-011,
 * RF-013, HU1–HU4, D-UC11-03 (idempotencia), D-UC11-07 (primera configuración).
 */
public class FinancialParametersService implements SaveFinancialParametersUseCase, LoadFinancialParametersUseCase {

    /**
     * Texto literal de la regla del depósito de garantía (solo lectura).
     * Fuente: UC11 plan §Reglas 5 + contrato UC11-obtener-parametros-financieros.md §3 y D-UC11-02.
     */
    static final String GUARANTEE_DEPOSIT_RULE = "10% de la tarifa base diaria";

    private final FinancialParametersRepository repository;
    private final HighSeasonCalendar highSeasonCalendar;
    private final ClockPort clock;

    public FinancialParametersService(FinancialParametersRepository repository,
                                      HighSeasonCalendar highSeasonCalendar,
                                      ClockPort clock) {
        this.repository = repository;
        this.highSeasonCalendar = highSeasonCalendar;
        this.clock = clock;
    }

    /**
     * Guarda los parámetros financieros globales como una nueva versión vigente (append-only).
     * Idempotente: si los cuatro valores son idénticos a la versión vigente, devuelve la versión
     * vigente sin crear una versión nueva [SPEC D-UC11-03].
     * La primera configuración crea la versión 1 [SPEC D-UC11-07].
     * Un fallo de persistencia se convierte en {@link ErrorCode#PARAMETERS_SAVE_FAILED} [SPEC HU4 esc. 3].
     */
    @Override
    public FinancialParametersResult save(FinancialParametersCommand command) {
        Optional<FinancialParameters> current = repository.findCurrent();

        // Idempotencia: si los cuatro valores son idénticos a la versión vigente, no crear versión nueva (D-UC11-03).
        if (current.isPresent() && isIdentical(current.get(), command)) {
            return toResult(current.get());
        }

        long nextVersion = current.map(FinancialParameters::versionNumber).orElse(0L) + 1;
        FinancialParameters newParams = FinancialParameters.create(
                UUID.randomUUID(),
                nextVersion,
                command.commissionPct(),
                command.insuranceFeePerPassenger(),
                command.weekendIncreasePct(),
                command.highSeasonIncreasePct(),
                null,
                Instant.now());

        try {
            FinancialParameters saved = repository.saveNewVersion(newParams);
            return toResult(saved);
        } catch (Exception ex) {
            throw new ContractException(ErrorCode.PARAMETERS_SAVE_FAILED,
                    "The financial parameters could not be saved. The previous configuration has been preserved.");
        }
    }

    /**
     * Carga la versión vigente de los parámetros financieros globales más los datos de solo lectura.
     * Si no existe ninguna versión, lanza {@link ContractException} con
     * {@link ErrorCode#PARAMETERS_NOT_CONFIGURED} → 404 [SPEC HU3 esc. 2].
     */
    @Override
    public FinancialParametersResult load() {
        FinancialParameters current = repository.findCurrent()
                .orElseThrow(() -> new ContractException(ErrorCode.PARAMETERS_NOT_CONFIGURED,
                        "The global financial parameters have not been configured yet."));

        return toResult(current);
    }

    // --- helpers ---

    /**
     * Compara los cuatro valores del command con los de la versión vigente usando equals() de los records.
     * Ambos VOs (Percentage y Money) normalizan a escala 4 en su constructor, por lo que equals() es
     * semánticamente equivalente a compareTo()==0 para detectar valores idénticos [UC11 plan D-UC11-03].
     */
    private static boolean isIdentical(FinancialParameters current, FinancialParametersCommand command) {
        return current.commissionPct().equals(command.commissionPct())
                && current.insuranceFeePerPassenger().equals(command.insuranceFeePerPassenger())
                && current.weekendIncreasePct().equals(command.weekendIncreasePct())
                && current.highSeasonIncreasePct().equals(command.highSeasonIncreasePct());
    }

    /**
     * Construye el result desde el modelo de dominio, añadiendo los dos datos de solo lectura:
     * la regla del depósito (texto fijo) y las ventanas de temporada alta del año actual [SPEC RF-009, RF-012].
     * Año actual según ClockPort.today() [UC11 plan T029/T031].
     */
    private FinancialParametersResult toResult(FinancialParameters params) {
        int currentYear = clock.today().getYear();
        List<String> windows = highSeasonCalendar.windowsForYear(currentYear)
                .stream()
                .map(FinancialParametersService::formatWindow)
                .toList();

        return new FinancialParametersResult(
                params.versionNumber(),
                params.commissionPct(),
                params.insuranceFeePerPassenger(),
                params.weekendIncreasePct(),
                params.highSeasonIncreasePct(),
                GUARANTEE_DEPOSIT_RULE,
                windows);
    }

    /**
     * Convierte una ventana de temporada alta al formato del contrato:
     * {@code MM-dd→MM-dd} para ventanas fijas y {@code "semana-santa"} para Semana Santa.
     * Fuente: UC11 plan §Reglas 6 + contrato UC11-obtener-parametros-financieros.md §4 [CONV].
     */
    static String formatWindow(HighSeasonWindow window) {
        if (window.type() == HighSeasonWindow.Type.HOLY_WEEK) {
            return "semana-santa";
        }
        return String.format("%02d-%02d\u2192%02d-%02d",
                window.startInclusive().getMonthValue(),
                window.startInclusive().getDayOfMonth(),
                window.endInclusive().getMonthValue(),
                window.endInclusive().getDayOfMonth());
    }
}

