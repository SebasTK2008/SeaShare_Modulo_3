package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.domain.model.ChargeRecord;

/**
 * Persistencia de registros de cobro (UC05).
 * Fuente: UC05 plan T039.
 * GAP: métodos no publicados; se deriva el guardado del registro inmutable.
 */
public interface ChargeRecordRepository {

    ChargeRecord save(ChargeRecord chargeRecord);
}
