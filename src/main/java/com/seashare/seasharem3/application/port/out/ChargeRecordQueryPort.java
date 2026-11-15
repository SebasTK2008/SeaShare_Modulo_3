package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.domain.model.ChargeRecord;

import java.util.Optional;
import java.util.UUID;

/**
 * Consultas de auditoría sobre charge_record para UC10/UC12/UC13.
 * Fuente: UC05 plan T039 (catálogo compartido).
 * GAP: métodos no publicados; se deriva la lectura por intención de cobro.
 */
public interface ChargeRecordQueryPort {

    Optional<ChargeRecord> findByChargeIntentId(UUID chargeIntentId);
}
