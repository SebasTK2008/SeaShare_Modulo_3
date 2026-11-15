package com.seashare.seasharem3.support;

import com.seashare.seasharem3.application.port.out.FinancialParametersRepository;
import com.seashare.seasharem3.domain.model.FinancialParameters;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Fake en memoria de {@link FinancialParametersRepository}: append-only y versión vigente = la de mayor
 * {@code version_number}. [CONV] núcleo compartido para las pruebas de UC01/UC02/UC04/UC11.
 */
public class InMemoryFinancialParametersRepository implements FinancialParametersRepository {

    private final Map<UUID, FinancialParameters> versions = new LinkedHashMap<>();

    @Override
    public Optional<FinancialParameters> findCurrent() {
        return versions.values().stream().max(Comparator.comparingLong(FinancialParameters::versionNumber));
    }

    @Override
    public Optional<FinancialParameters> findById(UUID parametersId) {
        return Optional.ofNullable(versions.get(parametersId));
    }

    @Override
    public FinancialParameters saveNewVersion(FinancialParameters params) {
        if (versions.containsKey(params.id())) {
            throw new IllegalStateException(
                    "financial_parameters is append-only: id already exists " + params.id());
        }
        versions.put(params.id(), params);
        return params;
    }
}
