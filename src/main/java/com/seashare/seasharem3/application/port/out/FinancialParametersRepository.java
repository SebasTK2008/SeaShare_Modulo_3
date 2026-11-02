package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.domain.model.FinancialParameters;

import java.util.Optional;
import java.util.UUID;

/**
 * Firma publicada en UC11 plan §Puertos expuestos; consumido por UC01, UC02 y UC04.
 * [CONV] núcleo: findById resuelve la versión referenciada por frozen_parameters_id (D-27; UC04 y UC10).
 */
public interface FinancialParametersRepository {

    /**
     * Serializa la asignación de version_number dentro de la transacción del llamador.
     * Los adaptadores no transaccionales pueden conservar la implementación vacía.
     */
    default void lockVersionAssignment() {
    }

    Optional<FinancialParameters> findCurrent();

    Optional<FinancialParameters> findById(UUID parametersId);

    FinancialParameters saveNewVersion(FinancialParameters params);
}
