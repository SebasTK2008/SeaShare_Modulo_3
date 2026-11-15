package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.CalculateReservationValueCommand;
import com.seashare.seasharem3.application.dto.ReservationValueResult;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.CalculateReservationValueUseCase;
import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.application.port.out.FinancialParametersRepository;
import com.seashare.seasharem3.application.port.out.ReservationInformationRepository;
import com.seashare.seasharem3.application.port.out.ReservationLockPort;
import com.seashare.seasharem3.domain.exception.FinancialParametersNotConfiguredException;
import com.seashare.seasharem3.domain.exception.ReservationInfoIncompleteException;
import com.seashare.seasharem3.domain.exception.ReservationInfoNotFoundException;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.service.ReservationValueCalculator;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.domain.valueobject.ReservationValue;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta UC04: recupera la información registrada por UC03, resuelve la versión de parámetros
 * (la referenciada si la hay, la vigente si no; general-plan D-17), calcula el desglose con
 * {@link ReservationValueCalculator} y deja los montos persistidos para UC05.
 *
 * <p>Orden de validación E3 → E4 → E6 (contracts/README §4.3): sin información → 404; sin versión de
 * parámetros o con valores ausentes → 503; información no calculable → 422. El 404 no registra el
 * fallo (estado transitorio esperado, D-UC04-06); el 503 y el 422 sí, vía {@link FailureRecorderPort}.
 * Sin cálculos parciales ante ningún error [SPEC RNF-003].</p>
 *
 * <p>Una sola transacción serializada con el bloqueo asesor por reserva (general-plan D-15, regla 18
 * del plan): el bloqueo se adquiere antes de leer, de modo que un <em>upsert</em> concurrente de
 * UC03 no puede invalidar la fila entre la lectura y la escritura; el adaptador lo re-adquiere al
 * escribir (bloqueo reentrante en la misma transacción).</p>
 *
 * Fuente: UC04 plan T011, reglas 9–20; SPEC RF-001–RF-009 y RNF-003.
 */
public class CalculateReservationValueService implements CalculateReservationValueUseCase {

    private static final String USE_CASE = "UC04";

    private final ReservationInformationRepository reservationInformationRepository;
    private final FinancialParametersRepository financialParametersRepository;
    private final FailureRecorderPort failureRecorder;
    private final ReservationLockPort reservationLock;
    private final ReservationValueCalculator calculator;
    private final Clock instantClock;

    public CalculateReservationValueService(ReservationInformationRepository reservationInformationRepository,
            FinancialParametersRepository financialParametersRepository, FailureRecorderPort failureRecorder,
            ReservationLockPort reservationLock, ReservationValueCalculator calculator, Clock instantClock) {
        this.reservationInformationRepository = Objects.requireNonNull(reservationInformationRepository,
                "reservationInformationRepository");
        this.financialParametersRepository = Objects.requireNonNull(financialParametersRepository,
                "financialParametersRepository");
        this.failureRecorder = Objects.requireNonNull(failureRecorder, "failureRecorder");
        this.reservationLock = Objects.requireNonNull(reservationLock, "reservationLock");
        this.calculator = Objects.requireNonNull(calculator, "calculator");
        this.instantClock = Objects.requireNonNull(instantClock, "instantClock");
    }

    @Override
    @Transactional
    public ReservationValueResult calculate(CalculateReservationValueCommand command) {
        Objects.requireNonNull(command, "command");
        ReservationId reservationId = Objects.requireNonNull(command.reservationId(), "command.reservationId");

        reservationLock.lock(reservationId);
        ReservationInformation information = reservationInformationRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new ReservationInfoNotFoundException(
                        "There is no registered information for reservation " + reservationId.value() + "."));
        FinancialParameters parameters = resolveParameters(information, reservationId);
        requireCalculable(information, reservationId);

        ReservationValue value = calculator.calculate(information, parameters);
        reservationInformationRepository.saveCalculatedAmounts(
                information.withCalculated(value, parameters.id(), instantClock.instant()));

        return new ReservationValueResult(reservationId, value.rentalAmount(), value.insuranceAmount(),
                value.guaranteeDeposit(), value.totalAmount());
    }

    /**
     * Primer cálculo → versión vigente; recálculo → versión referenciada, por lo que una versión
     * vigente más nueva no altera el desglose [general-plan D-17; RF-008; D-UC04-01]. Ambas lecturas
     * se resuelven con el repositorio compartido, sin join (D-27).
     */
    private FinancialParameters resolveParameters(ReservationInformation information, ReservationId reservationId) {
        UUID frozenParametersId = information.frozenParametersId();
        Optional<FinancialParameters> resolved = frozenParametersId != null
                ? financialParametersRepository.findById(frozenParametersId)
                : financialParametersRepository.findCurrent();

        FinancialParameters parameters = resolved.orElseThrow(
                () -> notConfigured(reservationId,
                        "There is no financial parameters version available for reservation "
                                + reservationId.value() + "."));
        if (parameters.insuranceFeePerPassenger() == null || parameters.commissionPct() == null) {
            throw notConfigured(reservationId,
                    "The financial parameters version used for reservation " + reservationId.value()
                            + " is missing the insurance fee or the commission percentage.");
        }
        return parameters;
    }

    /**
     * Vía defensiva de integridad [D-UC04-02]: UC03 nunca persiste información incompleta
     * ({@code base_rate NOT NULL}), por lo que con datos reales este 422 no es alcanzable; solo se
     * prueba con un doble. Nunca hay cálculo parcial.
     */
    private void requireCalculable(ReservationInformation information, ReservationId reservationId) {
        if (information.baseRate() == null) {
            throw infoIncomplete(reservationId);
        }
    }

    /** Registra el fallo (D-UC04-06) y devuelve la excepción que responde 422 sin cálculo parcial. */
    private ReservationInfoIncompleteException infoIncomplete(ReservationId reservationId) {
        failureRecorder.record(USE_CASE, reservationId.value(), ErrorCode.RESERVATION_INFO_INCOMPLETE.name(),
                payloadRef(reservationId));
        return new ReservationInfoIncompleteException("The registered information for reservation "
                + reservationId.value() + " is incomplete: base_rate is required to calculate the value.");
    }

    /** Registra el fallo (D-UC04-06) y devuelve la excepción que responde 503 sin cálculo parcial. */
    private FinancialParametersNotConfiguredException notConfigured(ReservationId reservationId, String detail) {
        failureRecorder.record(USE_CASE, reservationId.value(), ErrorCode.FINANCIAL_PARAMETERS_NOT_CONFIGURED.name(),
                payloadRef(reservationId));
        return new FinancialParametersNotConfiguredException(detail);
    }

    private static String payloadRef(ReservationId reservationId) {
        return "reservationId=" + reservationId.value();
    }
}
