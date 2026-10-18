package com.seashare.seasharem3.infrastructure.config.web;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.util.UUID;
import java.util.function.Function;

/**
 * Deserializa un UUID plano al VO identificador correspondiente.
 * Fuente: contracts/README §3.1 (identificadores UUID v4). [CONV] núcleo compartido.
 */
public class UuidScalarDeserializer<T> extends ValueDeserializer<T> {

    private final Function<UUID, T> factory;

    public UuidScalarDeserializer(Function<UUID, T> factory) {
        this.factory = factory;
    }

    @Override
    public T deserialize(JsonParser parser, DeserializationContext ctxt) {
        return factory.apply(UUID.fromString(parser.getValueAsString()));
    }
}
