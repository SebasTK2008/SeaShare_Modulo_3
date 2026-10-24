package com.seashare.seasharem3.infrastructure.config.web;

import com.seashare.seasharem3.domain.valueobject.Percentage;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.math.BigDecimal;

/**
 * Deserializa un string decimal (o número JSON) al VO {@link Percentage}.
 * Fuente: contracts/README §3.1. [CONV] núcleo compartido.
 */
public class PercentageDeserializer extends ValueDeserializer<Percentage> {

    @Override
    public Percentage deserialize(JsonParser parser, DeserializationContext ctxt) {
        return new Percentage(new BigDecimal(parser.getValueAsString()));
    }
}
