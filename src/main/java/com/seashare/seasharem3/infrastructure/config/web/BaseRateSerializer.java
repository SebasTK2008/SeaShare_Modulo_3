package com.seashare.seasharem3.infrastructure.config.web;

import com.seashare.seasharem3.domain.valueobject.BaseRate;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Serializa {@link BaseRate} como su monto en string decimal de 2 decimales HALF_UP.
 * Fuente: contracts/README §3.1. [CONV] núcleo compartido.
 */
public class BaseRateSerializer extends ValueSerializer<BaseRate> {

    @Override
    public void serialize(BaseRate value, JsonGenerator gen, SerializationContext ctxt) {
        gen.writeString(value.amount().toOutputAmount().toPlainString());
    }
}
