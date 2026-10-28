package com.seashare.seasharem3.infrastructure.config.web;

import com.seashare.seasharem3.domain.valueobject.Money;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.exc.MismatchedInputException;

import java.math.BigDecimal;

/**
 * Deserializa un string decimal al VO {@link Money}; los números JSON se rechazan para respetar
 * contracts/README §3.1 ("nunca número JSON").
 * Fuente: contracts/README §3.1. [CONV] núcleo compartido.
 */
public class MoneyDeserializer extends ValueDeserializer<Money> {

    @Override
    public Money deserialize(JsonParser parser, DeserializationContext ctxt) {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            throw MismatchedInputException.from(parser, Money.class, "must be a decimal string");
        }
        return new Money(new BigDecimal(parser.getValueAsString()));
    }
}
