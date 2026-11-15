package com.seashare.seasharem3.integration;

import com.seashare.seasharem3.TestcontainersConfiguration;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.parameters.FinancialParametersJpaRepository;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.parameters.FinancialParametersPersistenceAdapter;
import com.seashare.seasharem3.support.IntegrationTestProperties;
import com.seashare.seasharem3.support.RetryProbeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Aceptación de UC11 contra PostgreSQL real y el stack web completo: CE-001, CE-002, CE-004,
 * idempotencia, límites y errores por campo. La transacción de prueba hace rollback al final.
 * Requiere Docker; sin Docker se omite.
 */
@SpringBootTest(properties = {IntegrationTestProperties.OUTBOX_RELAY_INTERVAL,
        IntegrationTestProperties.RETRY_INITIAL_INTERVAL, IntegrationTestProperties.RETRY_MULTIPLIER,
        IntegrationTestProperties.RETRY_MAX_INTERVAL})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RetryProbeConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class UC11FinancialParametersWebIntegrationTest {

    private static final String ENDPOINT = "/api/v1/financial-parameters";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FinancialParametersPersistenceAdapter adapter;

    @Autowired
    private FinancialParametersJpaRepository jpaRepository;

    @Test
    void ce001_putThenGetReturnsPersistedValues() throws Exception {
        long expectedVersion = currentVersion() + 1;

        putParameters("15.00", "15000.00", "10.00", "25.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version_number").value(expectedVersion));

        mockMvc.perform(get(ENDPOINT).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version_number").value(expectedVersion))
                .andExpect(jsonPath("$.platform_commission_percentage").value("15.0000"))
                .andExpect(jsonPath("$.insurance_fee_per_passenger").value("15000.0000"))
                .andExpect(jsonPath("$.weekend_increase_percentage").value("10.0000"))
                .andExpect(jsonPath("$.high_season_increase_percentage").value("25.0000"))
                .andExpect(jsonPath("$.guarantee_deposit_rule").value("10% de la tarifa base diaria"))
                .andExpect(jsonPath("$.high_season_windows").isArray());
    }

    @Test
    void ce002_invalidPutKeepsCurrentVersion() throws Exception {
        long expectedVersion = currentVersion() + 1;
        putParameters("15.00", "15000.00", "10.00", "25.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version_number").value(expectedVersion));

        putParameters("150.00", "15000.00", "10.00", "25.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("platform_commission_percentage"));

        mockMvc.perform(get(ENDPOINT).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version_number").value(expectedVersion))
                .andExpect(jsonPath("$.platform_commission_percentage").value("15.0000"));
    }

    @Test
    void ce004_invalidPutDoesNotPersist() throws Exception {
        putParameters("15.00", "15000.00", "10.00", "25.00")
                .andExpect(status().isOk());
        long versionBefore = currentVersion();
        long countBefore = jpaRepository.count();

        putParameters("-1.00", "15000.00", "10.00", "25.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("platform_commission_percentage"));

        assertThat(jpaRepository.count()).isEqualTo(countBefore);
        assertThat(currentVersion()).isEqualTo(versionBefore);
    }

    @Test
    void putIsIdempotentAgainstRealDatabase() throws Exception {
        long countBefore = jpaRepository.count();
        long expectedVersion = currentVersion() + 1;

        putParameters("15.00", "15000.00", "10.00", "25.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version_number").value(expectedVersion));
        putParameters("15.00", "15000.00", "10.00", "25.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version_number").value(expectedVersion));

        assertThat(jpaRepository.count()).isEqualTo(countBefore + 1);
        assertThat(currentVersion()).isEqualTo(expectedVersion);
    }

    @Test
    void putGetPutPreservesFourDecimalValues() throws Exception {
        long expectedVersion = currentVersion() + 1;

        putParameters("15.1234", "15000.1234", "10.1234", "25.1234")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version_number").value(expectedVersion));

        mockMvc.perform(get(ENDPOINT).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platform_commission_percentage").value("15.1234"))
                .andExpect(jsonPath("$.insurance_fee_per_passenger").value("15000.1234"))
                .andExpect(jsonPath("$.weekend_increase_percentage").value("10.1234"))
                .andExpect(jsonPath("$.high_season_increase_percentage").value("25.1234"));

        putParameters("15.1234", "15000.1234", "10.1234", "25.1234")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version_number").value(expectedVersion));
    }

    @Test
    void putAcceptsBoundaryValuesZeroAndOneHundred() throws Exception {
        putParameters("0.00", "0.00", "0.00", "100.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version_number").isNumber());
    }

    @Test
    void putRejectsNonNumericValueWithFieldDetail() throws Exception {
        putParameters("abc", "15000.00", "10.00", "25.00")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("platform_commission_percentage"))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("decimal")));
    }

    private ResultActions putParameters(String commission, String insurance, String weekend, String highSeason)
            throws Exception {
        String body = """
                {
                  "platform_commission_percentage": "%s",
                  "insurance_fee_per_passenger": "%s",
                  "weekend_increase_percentage": "%s",
                  "high_season_increase_percentage": "%s"
                }
                """.formatted(commission, insurance, weekend, highSeason);

        return mockMvc.perform(put(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private long currentVersion() {
        return adapter.findCurrent().map(FinancialParameters::versionNumber).orElse(0L);
    }
}
