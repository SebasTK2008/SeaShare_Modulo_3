package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.domain.model.SettlementRecord;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistencia de settlement_record (UC10), inmutable y con settlement_intent_id obligatorio.
 * Fuente: UC10 plan T007/T012.
 */
public interface SettlementRecordRepository {

    SettlementRecord save(SettlementRecord settlementRecord);

    Optional<SettlementRecord> findBySettlementIntentId(UUID settlementIntentId);
}
