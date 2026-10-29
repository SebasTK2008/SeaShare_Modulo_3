package com.seashare.seasharem3.infrastructure.adapter.in.webhook;

import com.seashare.seasharem3.infrastructure.config.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Verifica la firma {@code x-signature} de las notificaciones de Mercado Pago.
 *
 * <p>El formato del header es {@code ts=<timestamp>,v1=<hash>} y el mensaje firmado es
 * {@code id:<data.id>;request-id:<x-request-id>;ts:<ts>;} firmado con HMAC-SHA256 y el secreto
 * configurado en el panel de la pasarela. Es un requisito de la integracion con la pasarela, no
 * control de acceso del sistema (general-plan §7.1).</p>
 *
 * <p>Fuente: contrato {@code pasarela-webhook-resultados.md} §2.</p>
 */
@Component
public class MercadoPagoSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoSignatureVerifier.class);

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TIMESTAMP_PREFIX = "ts=";
    private static final String SIGNATURE_PREFIX = "v1=";

    private final GatewayProperties properties;

    public MercadoPagoSignatureVerifier(GatewayProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * Indica si la firma corresponde a la notificacion. Una firma ausente, incompleta o que no
     * coincide con el secreto se considera invalida.
     */
    public boolean isValid(String dataId, String requestId, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank() || requestId == null || requestId.isBlank()) {
            return false;
        }
        String timestamp = extract(signatureHeader, TIMESTAMP_PREFIX);
        String signature = extract(signatureHeader, SIGNATURE_PREFIX);
        if (timestamp == null || signature == null) {
            return false;
        }
        String secret = properties.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("The gateway webhook secret is not configured; the notification cannot be verified");
            return false;
        }
        return MessageDigest.isEqual(expectedHash(secret, dataId, requestId, timestamp)
                .getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
    }

    private static String expectedHash(String secret, String dataId, String requestId, String timestamp) {
        String message = "id:" + dataId + ";request-id:" + requestId + ";ts:" + timestamp + ";";
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("The webhook signature cannot be computed", exception);
        }
    }

    /** Extrae el valor de una clave del header {@code ts=...,v1=...}. */
    private static String extract(String header, String prefix) {
        for (String part : header.split(",")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(prefix)) {
                return trimmed.substring(prefix.length());
            }
        }
        return null;
    }
}
