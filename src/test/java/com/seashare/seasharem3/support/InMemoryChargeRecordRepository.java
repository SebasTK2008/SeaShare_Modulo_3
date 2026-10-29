package com.seashare.seasharem3.support;

import com.seashare.seasharem3.application.port.out.ChargeRecordQueryPort;
import com.seashare.seasharem3.application.port.out.ChargeRecordRepository;
import com.seashare.seasharem3.domain.model.ChargeRecord;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Fake en memoria de {@link ChargeRecordRepository} y {@link ChargeRecordQueryPort}: guarda por
 * {@code id} y resuelve la consulta por intencion de cobro. Como la tabla real es inmutable, el
 * fake rechaza un segundo guardado con el mismo {@code charge_intent_id}.
 * [CONV] núcleo compartido para las pruebas de UC05/UC08/UC10/UC12/UC13.
 */
public class InMemoryChargeRecordRepository implements ChargeRecordRepository, ChargeRecordQueryPort {

    private final Map<UUID, ChargeRecord> records = new LinkedHashMap<>();

    @Override
    public ChargeRecord save(ChargeRecord chargeRecord) {
        boolean alreadyRecorded = records.values().stream()
                .anyMatch(stored -> stored.chargeIntentId().equals(chargeRecord.chargeIntentId()));
        if (alreadyRecorded) {
            throw new IllegalStateException(
                    "charge_record.charge_intent_id must be unique: " + chargeRecord.chargeIntentId());
        }
        records.put(chargeRecord.id(), chargeRecord);
        return chargeRecord;
    }

    @Override
    public Optional<ChargeRecord> findByChargeIntentId(UUID chargeIntentId) {
        return records.values().stream()
                .filter(record -> record.chargeIntentId().equals(chargeIntentId))
                .findFirst();
    }

    public int count() {
        return records.size();
    }
}
