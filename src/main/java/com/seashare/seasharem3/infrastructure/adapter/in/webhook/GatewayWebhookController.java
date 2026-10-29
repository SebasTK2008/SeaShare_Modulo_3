package com.seashare.seasharem3.infrastructure.adapter.in.webhook;

import com.seashare.seasharem3.application.dto.GatewayWebhookNotification;
import com.seashare.seasharem3.application.exception.ContractException;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.DispatchGatewayNotificationUseCase;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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
 *   <li>{@code 400 VALIDATION_ERROR} — payload mal formado o sin {@code action}, {@code data.id} o
 *       {@code type}.</li>
 *   <li>{@code 401 UNAUTHENTICATED} — la cabecera {@code x-signature} falta o no es valida.</li>
 *   <li>{@code 500 INTERNAL_ERROR} — la base de datos no esta disponible al persistir la
 *       notificacion: la pasarela reintentara.</li>
 * </ul>
 *
 * <p>Fuente: UC05 plan T019; contrato {@code pasarela-webhook-resultados.md} §2, §3 y §5.</p>
 */
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

    @PostMapping(path = "/api/v1/webhook/gateway", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> onNotification(
            @RequestParam(name = "data.id", required = false) String dataId,
            @RequestParam(name = "type", required = false) String type,
            @RequestHeader(name = "x-request-id", required = false) String requestId,
            @RequestHeader(name = "x-signature", required = false) String signature,
            @RequestBody(required = false) String body) {

        if (!signatureVerifier.isValid(dataId, requestId, signature)) {
            throw new ContractException(ErrorCode.UNAUTHENTICATED,
                    "The gateway notification signature is missing or invalid.");
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

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
