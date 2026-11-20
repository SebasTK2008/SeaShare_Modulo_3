package com.seashare.seasharem3.infrastructure.config.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.tags.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    @Test
    void exposesMetadataWithTheConfiguredVersionAndRelativeServer() {
        new ApplicationContextRunner()
                .withUserConfiguration(OpenApiConfig.class)
                .withPropertyValues("info.app.version=0.2.0-test")
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenAPI.class);
                    OpenAPI openApi = context.getBean(OpenAPI.class);
                    assertThat(openApi.getInfo().getTitle()).isEqualTo("SeaShare M3 · Finanzas");
                    assertThat(openApi.getInfo().getVersion()).isEqualTo("0.2.0-test");
                    assertThat(openApi.getServers()).singleElement()
                            .satisfies(server -> assertThat(server.getUrl()).isEqualTo("/"));
                    assertThat(openApi.getTags()).extracting(Tag::getName).containsExactly(
                            "UC01 · Estimaciones",
                            "UC04 · Valor calculado de reserva",
                            "UC06 · Confirmación de pago",
                            "UC11 · Parámetros financieros",
                            "UC12 · Registros financieros",
                            "UC13 · Informe financiero");
                });
    }
}
