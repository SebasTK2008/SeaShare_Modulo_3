package com.seashare.seasharem3.infrastructure.adapter.in.webhook;

import com.seashare.seasharem3.application.dto.GatewayWebhookNotification;
import com.seashare.seasharem3.application.exception.ContractException;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.DispatchGatewayNotificationUseCase;
import com.seashare.seasharem3.infrastructure.adapter.in.webhook.dto.doc.GatewayWebhookAcknowledgement;
import com.seashare.seasharem3.infrastructure.adapter.in.webhook.dto.doc.GatewayWebhookBody;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Adaptador de entrada REST de UC05: expone {@code POST /api/v1/webhook/gateway}, el endpoint de
 * entrada de la pasarela de pago. Solo invoca {@link DispatchGatewayNotificationUseCase} y nunca
 * accede a repositorios ni a servicios directamente (general-plan §3.4-3 y §3.4-5).
 *
 * <p>El webhook no es un endpoint de negocio de UC05: lo comparte con UC09 y UC10, que registran
 * su propio manejador para su tipo de evento. El paso 2 del contrato (consulta del estado real del
 * pago a la pasarela) ocurre de forma asincrona, en el worker de la cola interna, para que la
 * pasarela reciba el acuse de recibo sin esperar al sistema.</p>
 *
 * <ul>
 *   <li>{@code 200 OK} — notificacion aceptada, duplicada, con {@code data.id} o {@code type}
 *       desconocidos o con un tipo sin manejador (se registra en {@code operational_failure}).</li>
 *   <li>{@code 400 VALIDATION_ERROR} — payload mal formado, sin {@code action}, {@code data.id} o
 *       {@code type}, o con un {@code Content-Type} distinto de {@code application/json}.</li>
 *   <li>{@code 401 UNAUTHENTICATED} — la cabecera {@code x-signature} falta o no es valida.</li>
 *   <li>{@code 500 INTERNAL_ERROR} — la base de datos no esta disponible al persistir la
 *       notificacion: la pasarela reintentara.</li>
 * </ul>
 *
 * <p>Fuente: UC05 plan T019; contrato {@code pasarela-webhook-resultados.md} §2, §3 y §5.</p>
 */
@Tag(name = "Integración · Mercado Pago", description = "Webhook de la pasarela compartido por cobros y reembolsos; requiere firma HMAC")
@RestController
public class GatewayWebhookController {

    private final MercadoPagoSignatureVerifier signatureVerifier;
    private final DispatchGatewayNotificationUseCase dispatchNotification;
    private final JsonMapper jsonMapper;

    public GatewayWebhookController(MercadoPagoSignatureVerifier signatureVerifier,
            DispatchGatewayNotificationUseCase dispatchNotification, JsonMapper jsonMapper) {
        this.signatureVerifier = Objects.requireNonNull(signatureVerifier, "signatureVerifier");
        this.dispatchNotification = Objects.requireNonNull(dispatchNotification, "dispatchNotification");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
    }

    @Operation(operationId = "receiveGatewayNotification", summary = "Recibir notificación de Mercado Pago",
            description = "URL registrada en Mercado Pago a nivel de aplicación, topic payment. Verifica la firma antes de validar "
                    + "el payload; persiste la notificación en outbox y responde sin esperar el procesamiento financiero asíncrono. "
                    + "Un ID desconocido, un tipo sin manejador o una notificación duplicada se confirman con 200; "
                    + "no son prueba de aprobación del pago. No hay autenticación general: la firma es propia de esta integración. "
                    + "Try it out exige una firma calculada para los valores enviados; los ejemplos no son credenciales válidas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notificación recibida para procesamiento asíncrono",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = GatewayWebhookAcknowledgement.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: Content-Type no JSON, cuerpo inválido, action ausente/vacío "
                    + "o data.id/type ausentes en la query (después de validar la firma)",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = "#/components/schemas/ProblemDetails"))),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED: firma ausente/inválida, x-request-id ausente o secreto no configurado",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = "#/components/schemas/ProblemDetails"))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: fallo antes del acuse, por ejemplo al persistir en outbox; Mercado Pago reintenta",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = "#/components/schemas/ProblemDetails")))
    })
    @PostMapping(path = "/api/v1/webhook/gateway", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> onNotification(
            @Parameter(description = "ID del pago en Mercado Pago, usado para la firma y la consulta asíncrona; no es un UUID de reserva",
                    required = true, example = "1234567890")
            @RequestParam(name = "data.id", required = false) String dataId,
            @Parameter(description = "Tipo de evento de la query. La integración usa payment; otros tipos se confirman sin manejador",
                    required = true, example = "payment")
            @RequestParam(name = "type", required = false) String type,
            @Parameter(description = "Identificador de la petición de Mercado Pago usado en el manifiesto de firma", required = true,
                    example = "c78c0a7c-f1ba-4e89-8c4b-66dad4d10101")
            @RequestHeader(name = "x-request-id", required = false) String requestId,
            @Parameter(description = "Formato ts=<timestamp>,v1=<hash>. HMAC-SHA256 del manifiesto "
                    + "id:<data.id en minúsculas>;request-id:<x-request-id>;ts:<ts>; usando el secreto del webhook. "
                    + "Debe calcularse para esta petición; no se publica un secreto ni una firma reutilizable", required = true)
            @RequestHeader(name = "x-signature", required = false) String signature,
            @Parameter(hidden = true)
            @RequestHeader(name = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    description = "Objeto JSON con action. No se toman data.id ni type del cuerpo",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = GatewayWebhookBody.class),
                            examples = @ExampleObject(name = "Notificación payment", value = """
                                    {"action":"payment.updated","api_version":"v1","data":{"id":"1234567890"},
                                     "date_created":"2026-10-08T15:30:00Z","id":987654321,"live_mode":false,
                                     "type":"payment","user_id":"987654"}
                                    """)))
            @RequestBody(required = false) String body) {

        if (!signatureVerifier.isValid(dataId, requestId, signature)) {
            throw new ContractException(ErrorCode.UNAUTHENTICATED,
                    "The gateway notification signature is missing or invalid.");
        }
        if (!isJson(contentType)) {
            throw new ContractException(ErrorCode.VALIDATION_ERROR,
                    "The gateway notification requires Content-Type application/json.");
        }

        String action = extractAction(body);
        if (isBlank(dataId) || isBlank(type) || isBlank(action)) {
            throw new ContractException(ErrorCode.VALIDATION_ERROR,
                    "The gateway notification requires action, data.id and type.");
        }

        dispatchNotification.dispatch(new GatewayWebhookNotification(type, action, dataId));
        return ResponseEntity.ok(Map.of("status", "received"));
    }

    /** Lee la accion del cuerpo de la notificacion; un cuerpo ausente o mal formado no la aporta. */
    private String extractAction(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        JsonNode root;
        try {
            root = jsonMapper.readTree(body);
        } catch (RuntimeException exception) {
            return null;
        }
        if (root == null || !root.isObject()) {
            return null;
        }
        JsonNode action = root.get("action");
        return action == null || !action.isString() ? null : action.stringValue();
    }

    /** El contrato exige {@code application/json}; se aceptan parametros como {@code charset}. */
    private static boolean isJson(String contentType) {
        if (contentType == null) {
            return false;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT).trim();
        return normalized.equals(MediaType.APPLICATION_JSON_VALUE)
                || normalized.startsWith(MediaType.APPLICATION_JSON_VALUE + ";");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
