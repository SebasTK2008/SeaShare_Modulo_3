package com.seashare.seasharem3.infrastructure.adapter.out.fleet;

import com.seashare.seasharem3.domain.exception.BaseRateException;
import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;
import org.mapstruct.Mapper;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Mapea el contrato JSON de Flota al dominio de UC02.
 *
 * <p>Reglas (D-UC02-06): {@code base_rate} nula/ausente omite la embarcación; valor no numérico,
 * negativo o cero es una respuesta inválida y produce {@code FLEET_UNAVAILABLE}. No se mapea ningún
 * campo de tipo/categoría (RF-007).</p>
 */
@Mapper
public interface FleetRateMapper {

    default FleetBaseRatesRequestDto toRequest(Set<BoatId> boatIds) {
        Objects.requireNonNull(boatIds, "boatIds");
        List<UUID> ids = boatIds.stream().map(BoatId::value).toList();
        return new FleetBaseRatesRequestDto(ids);
    }

    default Map<BoatId, BaseRate> toDomain(FleetBaseRatesResponseDto response) {
        if (response == null || response.rates() == null) {
            throw invalidResponse("Fleet response does not contain a rates array");
        }

        Map<BoatId, BaseRate> result = new LinkedHashMap<>();
        for (BoatBaseRateDto dto : response.rates()) {
            if (dto == null || dto.boatId() == null) {
                throw invalidResponse("Fleet response contains a rate without boat_id");
            }
            if (dto.baseRate() == null || dto.baseRate().isBlank()) {
                continue;
            }
            result.put(new BoatId(dto.boatId()), toBaseRate(dto.baseRate(), dto.boatId()));
        }
        return Collections.unmodifiableMap(result);
    }

    default BaseRate toBaseRate(String rawBaseRate, UUID boatId) {
        BigDecimal amount;
        try {
            amount = new BigDecimal(rawBaseRate.trim());
        } catch (NumberFormatException exception) {
            throw invalidResponse("Fleet returned a non-numeric base_rate for boat " + boatId, exception);
        }
        if (amount.signum() <= 0) {
            throw invalidResponse("Fleet returned a non-positive base_rate for boat " + boatId);
        }
        return new BaseRate(new Money(amount));
    }

    private static BaseRateException invalidResponse(String message) {
        return new BaseRateException(BaseRateException.BaseRateFailureReason.FLEET_UNAVAILABLE, message);
    }

    private static BaseRateException invalidResponse(String message, Throwable cause) {
        return new BaseRateException(BaseRateException.BaseRateFailureReason.FLEET_UNAVAILABLE, message, cause);
    }
}
