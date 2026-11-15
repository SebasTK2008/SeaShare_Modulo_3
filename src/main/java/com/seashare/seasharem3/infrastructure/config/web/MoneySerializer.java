package com.seashare.seasharem3.infrastructure.config.web;

import com.seashare.seasharem3.domain.valueobject.Money;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Serializa {@link Money} como string decimal de 2 decimales HALF_UP (salida monetaria canónica).
 * Fuente: contracts/README §3.1. [CONV] núcleo compartido.
 */
public class MoneySerializer extends ValueSerializer<Money> {

    @Override
    public void serialize(Money value, JsonGenerator gen, SerializationContext ctxt) {
        gen.writeString(value.toOutputAmount().toPlainString());
    }
}
