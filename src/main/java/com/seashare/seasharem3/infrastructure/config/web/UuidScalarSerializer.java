package com.seashare.seasharem3.infrastructure.config.web;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.util.UUID;
import java.util.function.Function;

/**
 * Serializa un VO identificador (wrapper de UUID v4) como UUID plano, forma de los contratos.
 * Fuente: contracts/README §3.1 (identificadores UUID v4). [CONV] núcleo compartido.
 */
public class UuidScalarSerializer<T> extends ValueSerializer<T> {

    private final Function<T, UUID> extractor;

    public UuidScalarSerializer(Function<T, UUID> extractor) {
        this.extractor = extractor;
    }

    @Override
    public void serialize(T value, JsonGenerator gen, SerializationContext ctxt) {
        gen.writeString(extractor.apply(value).toString());
    }
}
