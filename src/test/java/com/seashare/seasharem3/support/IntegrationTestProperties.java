package com.seashare.seasharem3.support;

/**
 * Propiedades comunes de los tests de integración con Testcontainers.
 * [CONV] núcleo compartido: todos los tests de integración deben declarar el mismo conjunto en el mismo
 * orden para que Spring reutilice un único contexto cacheado (una sola pareja de contenedores).
 */
public final class IntegrationTestProperties {

    /** El job programado del relay no debe competir con los tests que invocan el relay manualmente. */
    public static final String OUTBOX_RELAY_INTERVAL = "seashare.outbox.relay-interval=1h";

    /**
     * Reintentos rápidos para los tests: se conserva {@code max-retries} de application.properties
     * (los 5 intentos del contrato) y solo se acortan las esperas.
     */
    public static final String RETRY_INITIAL_INTERVAL =
            "spring.rabbitmq.listener.simple.retry.initial-interval=20ms";
    public static final String RETRY_MULTIPLIER = "spring.rabbitmq.listener.simple.retry.multiplier=1";
    public static final String RETRY_MAX_INTERVAL = "spring.rabbitmq.listener.simple.retry.max-interval=20ms";

    private IntegrationTestProperties() {
    }
}
