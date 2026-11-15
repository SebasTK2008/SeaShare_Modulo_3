package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.domain.model.CommissionRecord;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistencia de commission_record (UC10), atomica con el registro de dispersion y sin duplicados.
 * Fuente: UC10 plan T007/T012.
 */
public interface CommissionRecordRepository {

    CommissionRecord save(CommissionRecord commissionRecord);

    Optional<CommissionRecord> findBySettlementRecordId(UUID settlementRecordId);
}
