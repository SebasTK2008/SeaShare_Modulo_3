package com.seashare.seasharem3.support;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.Argument;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sonda de reintentos: cola propia (con su DLQ) y un listener que siempre falla, para verificar en
 * integración la política del núcleo: reintentos con backoff, registro en {@code operational_failure}
 * y rechazo a la DLQ al agotarlos. Fuente: contracts/README §4.4.
 * [CONV] núcleo compartido: se importa desde todos los tests de integración para compartir el contexto
 * cacheado; la sonda solo consume su cola y no interfiere con la topología de producción.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RetryProbeConfiguration {

    /** Cola exclusiva de la sonda (ajena a contracts/README §3.5). */
    public static final String QUEUE = "finance.retry-probe.v1";
    public static final String DLQ = QUEUE + ".dlq";

    @Bean
    org.springframework.amqp.core.Queue retryProbeDeadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    AtomicInteger retryProbeAttempts() {
        return new AtomicInteger();
    }

    @Bean
    RetryProbeListener retryProbeListener(AtomicInteger retryProbeAttempts) {
        return new RetryProbeListener(retryProbeAttempts);
    }

    /** Listener que siempre falla: dispara la retry policy y, agotada, el recoverer del núcleo. */
    public static class RetryProbeListener {

        private final AtomicInteger attempts;

        RetryProbeListener(AtomicInteger attempts) {
            this.attempts = attempts;
        }

        @RabbitListener(queuesToDeclare = @Queue(name = QUEUE, arguments = {
                @Argument(name = "x-dead-letter-exchange", value = ""),
                @Argument(name = "x-dead-letter-routing-key", value = DLQ)}))
        public void alwaysFails(Message message) {
            attempts.incrementAndGet();
            throw new IllegalStateException("transient probe failure");
        }
    }
}
