package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.domain.model.RefundRecord;

/**
 * Persistencia de refund_record (UC09), inmutable y con refund_intent_id obligatorio.
 * Fuente: UC09 plan T005/T010.
 * GAP: firma no publicada; se deriva el guardado del registro confirmado.
 */
public interface RefundRecordRepository {

    RefundRecord save(RefundRecord refundRecord);
}
