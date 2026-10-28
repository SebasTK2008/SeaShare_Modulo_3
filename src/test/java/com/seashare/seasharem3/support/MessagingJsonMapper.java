package com.seashare.seasharem3.support;

import com.seashare.seasharem3.infrastructure.config.web.JacksonConfig;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link JsonMapper} de pruebas con la forma de los contratos AMQP. Reutiliza el customizer de
 * {@link JacksonConfig} para no divergir del mapper de producción (snake_case, campos desconocidos,
 * fechas ISO y escalares del dominio).
 */
public final class MessagingJsonMapper {

    private MessagingJsonMapper() {
    }

    public static JsonMapper create() {
        JsonMapper.Builder builder = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        new JacksonConfig().domainScalarsCustomizer().customize(builder);
        return builder.build();
    }
}
