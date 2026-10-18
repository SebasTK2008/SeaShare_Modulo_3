package com.seashare.seasharem3.infrastructure.config.web;

import com.seashare.seasharem3.domain.valueobject.Percentage;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.math.RoundingMode;

/**
 * Serializa {@link Percentage} como string decimal de 2 decimales HALF_UP (0–100).
 * Fuente: contracts/README §3.1 (porcentajes con dos decimales). [CONV] núcleo compartido.
 */
public class PercentageSerializer extends ValueSerializer<Percentage> {

    @Override
    public void serialize(Percentage value, JsonGenerator gen, SerializationContext ctxt) {
        gen.writeString(value.value().setScale(2, RoundingMode.HALF_UP).toPlainString());
    }
}
