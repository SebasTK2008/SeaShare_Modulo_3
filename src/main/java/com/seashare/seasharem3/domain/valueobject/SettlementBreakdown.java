package com.seashare.seasharem3.domain.valueobject;

/** Desglose y propietario usados al registrar una liquidacion confirmada. */
public record SettlementBreakdown(OwnerId ownerId, BoatId boatId, Money rentalAmount, Money insuranceAmount) {
}
