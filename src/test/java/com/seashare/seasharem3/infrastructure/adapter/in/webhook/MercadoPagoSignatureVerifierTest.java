package com.seashare.seasharem3.infrastructure.adapter.in.webhook;

import com.seashare.seasharem3.infrastructure.config.GatewayProperties;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class MercadoPagoSignatureVerifierTest {

    private static final String SECRET = "webhook-secret";
    private static final String DATA_ID = "1234567890";
    private static final String REQUEST_ID = "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10";

    private final MercadoPagoSignatureVerifier verifier = verifier(SECRET);

    private static MercadoPagoSignatureVerifier verifier(String secret) {
        GatewayProperties properties = new GatewayProperties();
        properties.setBaseUrl("https://api.mercadopago.com");
        properties.setWebhookSecret(secret);
        return new MercadoPagoSignatureVerifier(properties);
    }

    private static String signature(String dataId, String requestId, String timestamp) {
        String message = "id:" + dataId + ";request-id:" + requestId + ";ts:" + timestamp + ";";
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "ts=" + timestamp + ",v1=" + HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void acceptsTheSignatureOfTheNotification() {
        String signature = signature(DATA_ID, REQUEST_ID, "1765000000");

        assertThat(verifier.isValid(DATA_ID, REQUEST_ID, signature)).isTrue();
    }

    @Test
    void rejectsAMissingSignature() {
        assertThat(verifier.isValid(DATA_ID, REQUEST_ID, null)).isFalse();
        assertThat(verifier.isValid(DATA_ID, REQUEST_ID, "  ")).isFalse();
    }

    @Test
    void rejectsASignatureWithoutTimestampOrHash() {
        assertThat(verifier.isValid(DATA_ID, REQUEST_ID, "v1=abcdef")).isFalse();
        assertThat(verifier.isValid(DATA_ID, REQUEST_ID, "ts=1765000000")).isFalse();
    }

    @Test
    void rejectsASignatureOfAnotherNotification() {
        String signature = signature("9999999999", REQUEST_ID, "1765000000");

        assertThat(verifier.isValid(DATA_ID, REQUEST_ID, signature)).isFalse();
    }

    @Test
    void rejectsASignatureOfAnotherRequestId() {
        String signature = signature(DATA_ID, "otro-request-id", "1765000000");

        assertThat(verifier.isValid(DATA_ID, REQUEST_ID, signature)).isFalse();
    }

    @Test
    void rejectsTheNotificationWhenTheSecretIsNotConfigured() {
        assertThat(verifier("").isValid(DATA_ID, REQUEST_ID, signature(DATA_ID, REQUEST_ID, "1765000000"))).isFalse();
    }
}
