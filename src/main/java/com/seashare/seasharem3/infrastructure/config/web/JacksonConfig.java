package com.seashare.seasharem3.infrastructure.config.web;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.module.SimpleModule;

import java.math.BigDecimal;

/**
 * JSON de la API: todo {@link BigDecimal} se serializa como string decimal (contracts/README §3.1:
 * el dinero y los porcentajes nunca viajan como número JSON). Aplica al ObjectMapper web y al de AMQP
 * por compartir el JsonMapper de Spring Boot, pero NO a clientes de pasarela construidos aparte:
 * [CONV] los adaptadores externos (Mercado Pago) deben registrar su propia configuración si el
 * proveedor exige números.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer moneyAsPlainStringCustomizer() {
        SimpleModule module = new SimpleModule("seashare-money");
        module.addSerializer(BigDecimal.class, new BigDecimalPlainStringSerializer());
        return builder -> builder.addModule(module);
    }
}
