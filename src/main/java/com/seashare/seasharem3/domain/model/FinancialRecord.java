package com.seashare.seasharem3.domain.model;

import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.FinancialRecordType;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable row from {@code v_financial_records}; query-only domain data for UC12/UC13. */
public record FinancialRecord(UUID recordId, FinancialRecordType recordType, ReservationId reservationId,
                              OwnerId ownerId, BoatId boatId, Money amount, Instant createdAt,
                              String externalReference) {

    public FinancialRecord {
        Objects.requireNonNull(recordId, "recordId");
        Objects.requireNonNull(recordType, "recordType");
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(boatId, "boatId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
