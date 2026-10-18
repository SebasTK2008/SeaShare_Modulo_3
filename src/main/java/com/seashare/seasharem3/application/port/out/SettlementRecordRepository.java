package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.domain.model.SettlementRecord;

/**
 * Persistencia de settlement_record (UC10), inmutable y con settlement_intent_id obligatorio.
 * Fuente: UC10 plan T007/T012a.
 * GAP: firma no publicada; se deriva el guardado del registro confirmado.
 */
public interface SettlementRecordRepository {

    SettlementRecord save(SettlementRecord settlementRecord);
}
