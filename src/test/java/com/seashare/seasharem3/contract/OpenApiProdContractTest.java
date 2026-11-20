package com.seashare.seasharem3.contract;

import com.seashare.seasharem3.TestcontainersConfiguration;
import com.seashare.seasharem3.support.IntegrationTestProperties;
import com.seashare.seasharem3.support.RetryProbeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Perfil prod real, con infraestructura efímera y valores ficticios; no desactiva springdoc desde el test. */
@SpringBootTest(properties = {IntegrationTestProperties.OUTBOX_RELAY_INTERVAL,
        IntegrationTestProperties.RETRY_INITIAL_INTERVAL, IntegrationTestProperties.RETRY_MULTIPLIER,
        IntegrationTestProperties.RETRY_MAX_INTERVAL,
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/unused",
        "spring.datasource.username=unused", "spring.datasource.password=unused",
        "spring.rabbitmq.host=127.0.0.1", "spring.rabbitmq.port=1",
        "spring.rabbitmq.username=unused", "spring.rabbitmq.password=unused",
        "seashare.fleet.base-url=http://127.0.0.1:1",
        "seashare.gateway.base-url=http://127.0.0.1:1",
        "seashare.gateway.access-token=TEST-openapi-not-a-real-token",
        "seashare.gateway.webhook-secret=openapi-not-a-real-secret",
        "seashare.gateway.settlement-mode=simulated"})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RetryProbeConfiguration.class})
@ActiveProfiles("prod")
@Testcontainers(disabledWithoutDocker = true)
class OpenApiProdContractTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private Environment environment;

    @Test
    void productionProfileDisablesTheDocumentAndSwaggerRoutes() throws Exception {
        assertThat(environment.getProperty("springdoc.api-docs.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class)).isFalse();
        for (String path : new String[]{"/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/swagger-config",
                "/swagger-ui.html", "/swagger-ui/index.html", "/swagger-ui/swagger-ui-bundle.js"}) {
            mockMvc.perform(get(path)).andExpect(status().isNotFound());
        }
    }
}
