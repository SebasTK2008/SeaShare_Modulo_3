package com.seashare.seasharem3.domain.valueobject;

/** Propietario y embarcacion asociados a un registro financiero. */
public record OwnerBoat(OwnerId ownerId, BoatId boatId) {
}
