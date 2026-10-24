package com.seashare.seasharem3.infrastructure.config.web;

import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.Money;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.math.BigDecimal;

/**
 * Deserializa un string decimal (o número JSON) al VO {@link BaseRate}.
 * Fuente: contracts/README §3.1. [CONV] núcleo compartido.
 */
public class BaseRateDeserializer extends ValueDeserializer<BaseRate> {

    @Override
    public BaseRate deserialize(JsonParser parser, DeserializationContext ctxt) {
        return new BaseRate(new Money(new BigDecimal(parser.getValueAsString())));
    }
}
