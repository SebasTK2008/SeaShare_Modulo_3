package com.seashare.seasharem3.infrastructure.config.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Metadatos de la documentación REST derivada de los contratos Markdown del módulo. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI seashareOpenApi(@Value("${info.app.version}") String version) {
        return new OpenAPI()
                .info(new Info()
                        .title("SeaShare M3 · Finanzas")
                        .version(version)
                        .description("API REST del Módulo 3. Los contratos canónicos se encuentran en "
                                + "docs/technical-plan/contracts/."))
                .servers(List.of(new Server().url("/").description("Servidor actual")))
                .tags(List.of(
                        new Tag().name("UC01 · Estimaciones"),
                        new Tag().name("UC04 · Valor calculado de reserva"),
                        new Tag().name("UC06 · Confirmación de pago"),
                        new Tag().name("UC11 · Parámetros financieros"),
                        new Tag().name("UC12 · Registros financieros"),
                        new Tag().name("UC13 · Informe financiero")));
    }
}
