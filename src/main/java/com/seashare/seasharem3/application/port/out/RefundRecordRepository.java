package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.domain.model.RefundRecord;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistencia de refund_record (UC09), inmutable y con refund_intent_id obligatorio.
 * Fuente: UC09 plan T005/T010.
 */
public interface RefundRecordRepository {

    RefundRecord save(RefundRecord refundRecord);

    Optional<RefundRecord> findByRefundIntentId(UUID refundIntentId);
}
