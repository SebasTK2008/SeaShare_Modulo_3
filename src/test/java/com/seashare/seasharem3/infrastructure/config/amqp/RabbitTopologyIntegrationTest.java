package com.seashare.seasharem3.infrastructure.config.amqp;

import com.seashare.seasharem3.TestcontainersConfiguration;
import com.seashare.seasharem3.support.IntegrationTestProperties;
import com.seashare.seasharem3.support.RetryProbeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integración de la topología con RabbitMQ real: las seis colas de trabajo y sus DLQ quedan declaradas
 * en el broker al arrancar el contexto (contracts/README §3.5). Las definiciones exactas (quorum,
 * argumentos de DLQ y bindings) se fijan en {@link RabbitTopologyConfigTest}, que no necesita Docker.
 * Requiere Docker; en local se omiten.
 */
@SpringBootTest(properties = {IntegrationTestProperties.OUTBOX_RELAY_INTERVAL,
        IntegrationTestProperties.RETRY_INITIAL_INTERVAL, IntegrationTestProperties.RETRY_MULTIPLIER,
        IntegrationTestProperties.RETRY_MAX_INTERVAL})
@Import({TestcontainersConfiguration.class, RetryProbeConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
class RabbitTopologyIntegrationTest {

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Test
    void declaresTheSixWorkQueuesAndTheirDlqsInTheBroker() {
        for (String queue : workQueues()) {
            assertThat(amqpAdmin.getQueueProperties(queue)).as("queue %s", queue).isNotNull();
            assertThat(amqpAdmin.getQueueProperties(AmqpNames.deadLetterQueue(queue)))
                    .as("dlq of %s", queue).isNotNull();
        }
    }

    private static List<String> workQueues() {
        return List.of(AmqpNames.QUEUE_RESERVATION_INFO, AmqpNames.QUEUE_RESERVATION_STATUS,
                AmqpNames.QUEUE_GUARANTEE_DISPUTE, AmqpNames.QUEUE_CHARGE_COMMAND, AmqpNames.QUEUE_REFUND_COMMAND,
                AmqpNames.QUEUE_SETTLEMENT_COMMAND);
    }
}
