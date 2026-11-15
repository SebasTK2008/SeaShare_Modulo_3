package com.seashare.seasharem3.infrastructure.config.web;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Serializa {@link BigDecimal} como string decimal de 2 decimales HALF_UP (salida monetaria canónica,
 * nunca número JSON ni notación científica).
 * Fuente: contracts/README §3.1 (dinero y porcentajes como string decimal; escala 2 de salida). [CONV] núcleo compartido.
 */
public class BigDecimalPlainStringSerializer extends ValueSerializer<BigDecimal> {

    @Override
    public void serialize(BigDecimal value, JsonGenerator gen, SerializationContext ctxt) {
        gen.writeString(value.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }
}
