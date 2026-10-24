package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.domain.model.CommissionRecord;

/**
 * Persistencia de commission_record (UC10), atómico con el registro de dispersión y sin duplicados.
 * Fuente: UC10 plan T007/T012a.
 * GAP: firma no publicada; se deriva el guardado de la comisión.
 */
public interface CommissionRecordRepository {

    CommissionRecord save(CommissionRecord commissionRecord);
}
