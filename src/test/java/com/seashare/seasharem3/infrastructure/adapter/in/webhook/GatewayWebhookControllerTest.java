package com.seashare.seasharem3.infrastructure.adapter.in.webhook;

import com.seashare.seasharem3.application.dto.GatewayWebhookNotification;
import com.seashare.seasharem3.application.port.in.DispatchGatewayNotificationUseCase;
import com.seashare.seasharem3.infrastructure.adapter.in.web.error.ApiProblemDetailsFactory;
import com.seashare.seasharem3.infrastructure.adapter.in.web.error.GlobalExceptionHandler;
import com.seashare.seasharem3.infrastructure.config.GatewayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP del webhook de la pasarela: verificacion de la firma, validacion del payload, acuse
 * de recibo y despacho por {@code type}.
 * Fuente: UC05 plan T019; contrato {@code pasarela-webhook-resultados.md} §2, §3 y §5.
 */
class GatewayWebhookControllerTest {

    private static final String SECRET = "webhook-secret";
    private static final String DATA_ID = "1234567890";
    private static final String REQUEST_ID = "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10";
    private static final String TIMESTAMP = "1765000000";

    private final RecordingDispatcher dispatcher = new RecordingDispatcher();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        GatewayProperties properties = new GatewayProperties();
        properties.setBaseUrl("https://api.mercadopago.com");
        properties.setWebhookSecret(SECRET);
        GatewayWebhookController controller = new GatewayWebhookController(
                new MercadoPagoSignatureVerifier(properties), dispatcher, JsonMapper.builder().build());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(new ApiProblemDetailsFactory()))
                .build();
    }

    private static String signature() {
        String message = "id:" + DATA_ID + ";request-id:" + REQUEST_ID + ";ts:" + TIMESTAMP + ";";
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "ts=" + TIMESTAMP + ",v1="
                    + HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder notification(
            String signature, String body) {
        return post("/api/v1/webhook/gateway")
                .param("data.id", DATA_ID)
                .param("type", "payment")
                .header("x-request-id", REQUEST_ID)
                .header("x-signature", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    @Test
    void acceptsAValidNotificationAndDispatchesItByType() throws Exception {
        mockMvc.perform(notification(signature(),
                "{\"action\":\"payment.updated\",\"api_version\":\"v1\",\"data\":{\"id\":\"" + DATA_ID + "\"},"
                        + "\"type\":\"payment\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("received"));

        assertThat(dispatcher.notifications).hasSize(1);
        assertThat(dispatcher.notifications.get(0).type()).isEqualTo("payment");
        assertThat(dispatcher.notifications.get(0).action()).isEqualTo("payment.updated");
        assertThat(dispatcher.notifications.get(0).dataId()).isEqualTo(DATA_ID);
    }

    @Test
    void rejectsANotificationWithoutAValidSignature() throws Exception {
        mockMvc.perform(notification("ts=" + TIMESTAMP + ",v1=000000", "{\"action\":\"payment.updated\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertThat(dispatcher.notifications).isEmpty();
    }

    @Test
    void rejectsANotificationWithoutSignatureHeader() throws Exception {
        mockMvc.perform(post("/api/v1/webhook/gateway")
                .param("data.id", DATA_ID)
                .param("type", "payment")
                .header("x-request-id", REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"payment.updated\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(dispatcher.notifications).isEmpty();
    }

    @Test
    void rejectsAPayloadWithoutAction() throws Exception {
        mockMvc.perform(notification(signature(), "{\"api_version\":\"v1\",\"type\":\"payment\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(dispatcher.notifications).isEmpty();
    }

    @Test
    void rejectsAMalformedPayload() throws Exception {
        mockMvc.perform(notification(signature(), "not a json payload"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(dispatcher.notifications).isEmpty();
    }

    @Test
    void rejectsANotificationWithoutType() throws Exception {
        mockMvc.perform(post("/api/v1/webhook/gateway")
                .param("data.id", DATA_ID)
                .header("x-request-id", REQUEST_ID)
                .header("x-signature", signature())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"payment.updated\"}"))
                .andExpect(status().isBadRequest());

        assertThat(dispatcher.notifications).isEmpty();
    }

    @Test
    void answersWithInternalErrorWhenTheNotificationCannotBePersisted() throws Exception {
        dispatcher.failure = new IllegalStateException("the database is not available");

        mockMvc.perform(notification(signature(), "{\"action\":\"payment.updated\"}"))
                .andExpect(status().isInternalServerError());

        assertThat(dispatcher.notifications).isEmpty();
    }

    /** Doble del puerto de despacho: el adaptador web solo invoca el puerto de entrada. */
    private static final class RecordingDispatcher implements DispatchGatewayNotificationUseCase {

        private final List<GatewayWebhookNotification> notifications = new ArrayList<>();

        private RuntimeException failure;

        @Override
        public void dispatch(GatewayWebhookNotification notification) {
            if (failure != null) {
                throw failure;
            }
            notifications.add(notification);
        }
    }
}
