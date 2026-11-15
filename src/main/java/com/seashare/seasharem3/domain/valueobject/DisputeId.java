package com.seashare.seasharem3.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

/** Identificador de disputa (wrapper de UUID v4). Fuente: contrato events/UC08-disputa-garantia.md §2. */
public record DisputeId(UUID value) {

    public DisputeId {
        Objects.requireNonNull(value, "value");
    }
}
