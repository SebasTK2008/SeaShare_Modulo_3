package com.seashare.seasharem3.support;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link JsonMapper} de pruebas con la forma de los contratos AMQP: {@code snake_case} y fallo ante
 * campos desconocidos, igual que el mapper de Spring Boot configurado por {@code JacksonConfig}.
 */
public final class MessagingJsonMapper {

    private MessagingJsonMapper() {
    }

    public static JsonMapper create() {
        return JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                .build();
    }
}
