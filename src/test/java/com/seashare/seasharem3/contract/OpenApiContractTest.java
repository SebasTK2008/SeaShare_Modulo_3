package com.seashare.seasharem3.contract;

import com.seashare.seasharem3.TestcontainersConfiguration;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.support.IntegrationTestProperties;
import com.seashare.seasharem3.support.RetryProbeConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato generado por el stack completo en dev/qa; no ejecuta operaciones financieras. */
@SpringBootTest(properties = {IntegrationTestProperties.OUTBOX_RELAY_INTERVAL,
        IntegrationTestProperties.RETRY_INITIAL_INTERVAL, IntegrationTestProperties.RETRY_MULTIPLIER,
        IntegrationTestProperties.RETRY_MAX_INTERVAL})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RetryProbeConfiguration.class})
@ActiveProfiles({"dev", "qa"})
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper mapper;
    @Value("${info.app.version}")
    private String version;

    private JsonNode document;

    @BeforeAll
    void loadGeneratedDocument() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs").header("X-Correlation-Id", "openapi-contract"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        document = mapper.readTree(json);
        // Artefacto de diagnóstico del build, no snapshot versionado ni fuente del contrato.
        Files.writeString(Path.of("target", "openapi.json"), json);
    }

    @Test
    void publishesExactlyTheDocumentedOperationsWithUniqueIdsAndTags() {
        assertThat(document.path("openapi").asString()).startsWith("3.");
        assertThat(document.at("/info/version").asString()).isEqualTo(version).doesNotContain("@");
        assertThat(document.at("/servers/0/url").asString()).isEqualTo("/");
        Map<String, Set<String>> expected = Map.of(
                "/api/v1/estimates/batch", Set.of("post"),
                "/api/v1/estimates/individual", Set.of("post"),
                "/api/v1/financial-parameters", Set.of("get", "put"),
                "/api/v1/financial-records", Set.of("get"),
                "/api/v1/reservations/{reservationId}/payment-confirmation", Set.of("get"),
                "/api/v1/reservations/{reservationId}/calculated-value", Set.of("post"),
                "/api/v1/financial-report", Set.of("get"),
                "/api/v1/financial-report/export", Set.of("get"),
                "/api/v1/webhook/gateway", Set.of("post"));
        assertThat(document.path("paths").propertyNames()).containsExactlyInAnyOrderElementsOf(expected.keySet());
        Set<String> ids = new HashSet<>();
        expected.forEach((path, methods) -> {
            assertThat(document.path("paths").path(path).propertyNames()).containsExactlyInAnyOrderElementsOf(methods);
            methods.forEach(method -> {
                JsonNode operation = operation(path, method);
                assertThat(operation.path("summary").asString()).isNotBlank();
                assertThat(operation.path("description").asString()).isNotBlank();
                assertThat(operation.path("tags").isArray()).isTrue();
                String id = operation.path("operationId").asString();
                assertThat(id).isNotBlank();
                assertThat(ids.add(id)).as("operationId único: %s", id).isTrue();
            });
        });
        assertThat(ids).hasSize(10);
    }

    @Test
    void allReferencesResolveAndCorrelationIsDocumentedOnEveryResponse() {
        verifyReferences(document);
        document.path("paths").properties().forEach(path -> path.getValue().properties().forEach(method -> {
            JsonNode operation = method.getValue();
            List<JsonNode> correlations = new ArrayList<>();
            operation.path("parameters").forEach(parameter -> {
                JsonNode resolved = resolve(parameter);
                if (resolved.path("name").asString().equalsIgnoreCase("X-Correlation-Id")) {
                    correlations.add(resolved);
                }
            });
            assertThat(correlations).hasSize(1);
            assertThat(correlations.getFirst().path("required").asBoolean()).isFalse();
            assertType(correlations.getFirst().path("schema"), "string");
            assertThat(correlations.getFirst().at("/schema/format").isMissingNode()).isTrue();
            operation.path("responses").forEach(response -> {
                JsonNode header = resolve(response).at("/headers/X-Correlation-Id");
                assertThat(header.isMissingNode()).isFalse();
                assertType(resolve(header).path("schema"), "string");
            });
        }));
    }

    @Test
    void moneyIdsAndDatesMatchTheWireFormatIncludingNestedArrays() {
        for (String field : List.of("boat_id", "reservation_id")) {
            String model = field.equals("boat_id") ? "SingleEstimateResponse" : "ReservationValueResponse";
            assertType(property(model, field), "string");
            assertThat(property(model, field).path("format").asString()).isEqualTo("uuid");
        }
        assertType(property("SingleEstimateResponse", "estimated_total"), "string");
        for (String field : List.of("rental_amount", "insurance_amount", "guarantee_deposit_amount", "total_amount")) {
            assertType(property("ReservationValueResponse", field), "string");
        }
        for (String field : List.of("platform_commission_percentage", "insurance_fee_per_passenger",
                "weekend_increase_percentage", "high_season_increase_percentage")) {
            assertType(property("FinancialParametersRequest", field), "string");
            assertType(property("FinancialParametersResponse", field), "string");
            assertThat(property("FinancialParametersResponse", field).path("example").asString()).matches("\\d+\\.\\d{4}");
        }
        JsonNode ids = property("BatchEstimateRequest", "boat_ids");
        assertType(ids, "array");
        assertType(ids.path("items"), "string");
        assertThat(ids.at("/items/format").asString()).isEqualTo("uuid");
        assertThat(ids.path("minItems").asInt(0)).isZero();
        assertThat(ids.path("uniqueItems").asBoolean(false)).isFalse();
        assertType(property("BatchEstimateItem", "estimated_total"), "string");
        assertThat(property("SingleEstimateRequest", "start_date").path("format").asString()).isEqualTo("date");
        assertThat(property("FinancialRecord", "created_at").path("format").asString()).isEqualTo("date-time");
    }

    @Test
    void requiredNullableFieldsAndExamplesRespectTheContracts() {
        assertThat(schema("SingleEstimateResponse").path("required").valueStream().map(JsonNode::asString).toList())
                .contains("warning", "estimated_total");
        assertThat(property("SingleEstimateResponse", "warning").path("example").asString())
                .isEqualTo("Valor estimado. El valor incluye el seguro náutico, pero no incluye el depósito de garantía ni penalidades o ajustes derivados de cambios posteriores de la reserva");
        for (String field : List.of("authorized_amount", "captured_amount", "released_amount", "charged_amount")) {
            JsonNode amount = property("PaymentConfirmationResponse", field);
            assertType(amount, "string");
            assertNullable(amount);
        }
        assertNullable(property("FinancialRecord", "external_reference"));
        assertNullable(property("FinancialReportComparison", "percentage_variation"));
        assertThat(property("PaymentConfirmationResponse", "status").path("enum").valueStream().map(JsonNode::asString).toList())
                .containsExactlyInAnyOrder("IN_PROCESS", "APPROVED", "REJECTED", "CANCELLED", "EXPIRED", "UNKNOWN");
        assertThat(schema("SingleEstimateRequest").path("additionalProperties").asBoolean(true)).isFalse();
    }

    @Test
    void queryParametersExposeEnumsLimitsAndConditionalRequirements() {
        JsonNode records = operation("/api/v1/financial-records", "get");
        assertType(parameter(records, "page").path("schema"), "integer");
        assertType(parameter(records, "size").path("schema"), "integer");
        assertThat(parameter(records, "page").path("required").asBoolean()).isTrue();
        assertThat(parameter(records, "page").at("/schema/minimum").asInt()).isEqualTo(1);
        assertThat(parameter(records, "size").at("/schema/maximum").asInt()).isEqualTo(100);
        assertThat(parameter(records, "size").at("/schema/default").asInt()).isEqualTo(10);
        assertThat(parameter(records, "scope").at("/schema/enum").valueStream().map(JsonNode::asString).toList())
                .containsExactlyInAnyOrder("OWNER", "PLATFORM");
        assertThat(parameter(records, "owner_id").path("required").asBoolean()).isFalse();
        assertThat(parameter(records, "owner_id").path("description").asString()).contains("scope=OWNER");
        JsonNode report = operation("/api/v1/financial-report", "get");
        JsonNode export = operation("/api/v1/financial-report/export", "get");
        assertThat(parameter(report, "period").path("required").asBoolean()).isFalse();
        assertThat(parameter(export, "period").path("required").asBoolean()).isTrue();
        assertThat(parameter(report, "periodicity").at("/schema/enum").valueStream().map(JsonNode::asString).toList())
                .containsExactlyInAnyOrder("SEMI_MONTHLY", "MONTHLY", "QUARTERLY");
        assertThat(parameter(report, "period").path("examples").size()).isEqualTo(3);
    }

    @Test
    void everyOperationDeclaresOnlyItsContractErrorsWithProblemJson() {
        Map<String, Set<String>> statuses = Map.of(
                "estimateBatch", Set.of("200", "400", "503", "500"),
                "estimateIndividual", Set.of("200", "400", "422", "503", "500"),
                "getFinancialParameters", Set.of("200", "404", "500"),
                "saveFinancialParameters", Set.of("200", "400", "500"),
                "queryFinancialRecords", Set.of("200", "400", "404", "500"),
                "getPaymentConfirmation", Set.of("200", "400", "404", "500"),
                "calculateReservationValue", Set.of("200", "400", "404", "422", "503", "500"),
                "getFinancialReport", Set.of("200", "400", "500"),
                "exportFinancialReport", Set.of("200", "400", "500"),
                "receiveGatewayNotification", Set.of("200", "400", "401", "500"));
        document.path("paths").forEach(path -> path.forEach(operation -> {
            JsonNode responses = operation.path("responses");
            assertThat(responses.propertyNames()).containsExactlyInAnyOrderElementsOf(statuses.get(operation.path("operationId").asString()));
            responses.properties().forEach(response -> {
                if (!response.getKey().equals("200")) {
                    assertThat(response.getValue().path("$ref").asString()).startsWith("#/components/responses/");
                    JsonNode body = resolve(response.getValue()).path("content");
                    assertThat(body.propertyNames()).containsExactly("application/problem+json");
                    assertThat(body.path("application/problem+json").at("/schema/$ref").asString())
                            .isEqualTo("#/components/schemas/ProblemDetails");
                    JsonNode examples = body.path("application/problem+json").path("examples");
                    assertThat(examples.isObject()).isTrue();
                    assertThat(examples.isEmpty()).isFalse();
                    examples.forEach(example -> {
                        JsonNode value = resolve(example).path("value");
                        ErrorCode code = ErrorCode.valueOf(value.path("code").asString());
                        assertThat(value.path("status").asInt()).isEqualTo(Integer.parseInt(response.getKey()));
                        assertThat(code.httpStatus()).isEqualTo(value.path("status").asInt());
                        assertThat(value.path("retryable").asBoolean()).isEqualTo(code.retryable());
                    });
                }
            });
        }));
        assertThat(schema("ProblemDetails").path("properties").propertyNames()).contains("code", "retryable", "errors");
        assertThat(schema("ProblemDetails").path("required").valueStream().map(JsonNode::asString).toList()).doesNotContain("errors", "instance");
    }

    @Test
    void documentsCsvAndWebhookWithoutChangingTheirRuntimeBodies() {
        JsonNode export = operation("/api/v1/financial-report/export", "get");
        JsonNode csv = export.at("/responses/200/content/text~1csv");
        assertType(csv.path("schema"), "string");
        assertThat(csv.path("examples").valueStream().findFirst().orElseThrow().path("value").asString())
                .startsWith("Fecha de Generacion,Alcance,Periodicidad,Periodo");
        assertThat(export.at("/responses/200/headers/Content-Disposition").isMissingNode()).isFalse();
        JsonNode webhook = operation("/api/v1/webhook/gateway", "post");
        for (String name : List.of("data.id", "type", "x-request-id", "x-signature")) {
            assertThat(parameter(webhook, name).path("required").asBoolean()).as(name).isTrue();
        }
        assertThat(webhook.at("/requestBody/required").asBoolean()).isTrue();
        assertThat(resolve(webhook.at("/requestBody/content/application~1json/schema")).path("required").valueStream()
                .map(JsonNode::asString).toList()).containsExactly("action");
        assertThat(operation("/api/v1/reservations/{reservationId}/calculated-value", "post").path("requestBody").isMissingNode()).isTrue();
    }

    @Test
    void servesUiAndItsConfigurationAndAssets() throws Exception {
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("swagger-ui")));
        mockMvc.perform(get("/swagger-ui/swagger-ui-bundle.js")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
    }

    private JsonNode operation(String path, String method) {
        return document.path("paths").path(path).path(method);
    }

    private JsonNode schema(String name) {
        JsonNode schema = document.at("/components/schemas/" + name);
        assertThat(schema.isMissingNode()).as("schema %s", name).isFalse();
        return schema;
    }

    private JsonNode property(String model, String field) {
        JsonNode property = resolve(schema(model).path("properties").path(field));
        assertThat(property.isMissingNode()).as("%s.%s", model, field).isFalse();
        return property;
    }

    private JsonNode parameter(JsonNode operation, String name) {
        return operation.path("parameters").valueStream().map(this::resolve)
                .filter(parameter -> name.equals(parameter.path("name").asString())).findFirst().orElseThrow();
    }

    private JsonNode resolve(JsonNode node) {
        return node.has("$ref") ? document.at(node.path("$ref").asString().substring(1)) : node;
    }

    private void verifyReferences(JsonNode node) {
        if (node.has("$ref")) {
            String ref = node.path("$ref").asString();
            assertThat(ref).startsWith("#/");
            assertThat(document.at(ref.substring(1)).isMissingNode()).as("Referencia válida: %s", ref).isFalse();
        }
        if (node.isContainer()) {
            node.forEach(this::verifyReferences);
        }
    }

    private static void assertType(JsonNode schema, String expected) {
        JsonNode type = schema.path("type");
        List<String> types = type.isArray() ? type.valueStream().map(JsonNode::asString).toList() : List.of(type.asString());
        assertThat(types).contains(expected).doesNotContain("object");
    }

    private static void assertNullable(JsonNode schema) {
        boolean nullable = schema.path("nullable").asBoolean()
                || schema.path("type").valueStream().anyMatch(type -> type.asString().equals("null"));
        assertThat(nullable).as("Campo nullable: %s", schema).isTrue();
    }
}
