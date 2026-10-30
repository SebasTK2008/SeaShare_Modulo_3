package com.seashare.seasharem3.support;

import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.valueobject.ChargeIntentStatus;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Fake en memoria de {@link ChargeIntentRepository}: guarda por {@code id} (el mismo id actualiza la
 * intención, como el merge de JPA) y resuelve las consultas publicadas al núcleo.
 * {@code findApprovedByReservationId} devuelve la intención CAPTURED más reciente (el cobro aprobado que
 * consumen UC09/UC10). {@code save} rechaza una clave idempotente ya presente, como la constraint
 * {@code UNIQUE(idempotency_key)} del esquema. [CONV] núcleo compartido para las pruebas de
 * UC05/UC06/UC09/UC10.
 */
public class InMemoryChargeIntentRepository implements ChargeIntentRepository {

    private final Map<UUID, ChargeIntent> intents = new LinkedHashMap<>();

    @Override
    public Optional<ChargeIntent> findById(UUID id) {
        return Optional.ofNullable(intents.get(id));
    }

    @Override
    public Optional<ChargeIntent> findLatestByReservationId(ReservationId reservationId) {
        return intents.values().stream()
                .filter(intent -> intent.reservationId().equals(reservationId))
                .max(Comparator.comparing(ChargeIntent::createdAt).thenComparing(ChargeIntent::id));
    }

    @Override
    public Optional<ChargeIntent> findApprovedByReservationId(ReservationId reservationId) {
        return intents.values().stream()
                .filter(intent -> intent.reservationId().equals(reservationId))
                .filter(intent -> intent.status() == ChargeIntentStatus.CAPTURED)
                .max(Comparator.comparing(ChargeIntent::createdAt));
    }

    @Override
    public Optional<ChargeIntent> findByExternalReference(String externalReference) {
        return intents.values().stream()
                .filter(intent -> externalReference.equals(intent.externalReference()))
                .findFirst();
    }

    @Override
    public Optional<ChargeIntent> findByIdempotencyKey(IdempotencyKey idempotencyKey) {
        return intents.values().stream()
                .filter(intent -> intent.idempotencyKey().equals(idempotencyKey))
                .findFirst();
    }

    @Override
    public List<ChargeIntent> findByStatusIn(Collection<ChargeIntentStatus> statuses) {
        return intents.values().stream()
                .filter(intent -> statuses.contains(intent.status()))
                .toList();
    }

    @Override
    public ChargeIntent save(ChargeIntent chargeIntent) {
        boolean sameKeyInAnotherIntent = intents.values().stream()
                .anyMatch(stored -> !stored.id().equals(chargeIntent.id())
                        && stored.idempotencyKey().equals(chargeIntent.idempotencyKey()));
        if (sameKeyInAnotherIntent) {
            throw new IllegalStateException(
                    "charge_intent.idempotency_key must be unique: " + chargeIntent.idempotencyKey().value());
        }
        intents.put(chargeIntent.id(), chargeIntent);
        return chargeIntent;
    }
}
