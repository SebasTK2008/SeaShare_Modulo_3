package com.seashare.seasharem3.infrastructure.config.web;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.math.BigDecimal;

/**
 * Serializa {@link BigDecimal} como string decimal (nunca número JSON), sin notación científica.
 * Fuente: contracts/README §3.1 (dinero y porcentajes como string decimal). [CONV] núcleo compartido.
 */
public class BigDecimalPlainStringSerializer extends ValueSerializer<BigDecimal> {

    @Override
    public void serialize(BigDecimal value, JsonGenerator gen, SerializationContext ctxt) {
        gen.writeString(value.toPlainString());
    }
}
