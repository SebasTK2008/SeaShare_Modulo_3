package com.seashare.seasharem3.infrastructure.config.amqp;

import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.infrastructure.config.web.JacksonConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.core.ParameterizedTypeReference;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Definiciones congeladas de la topología AMQP (colas quorum con DLQ, exchanges y bindings de
 * contracts/README §3.5) y el converter de los listeners, que debe recibir el JsonMapper de Spring Boot
 * (snake_case + escalares del dominio). [CONV] núcleo compartido.
 */
class RabbitTopologyConfigTest {

    private static final UUID RESERVATION_UUID = UUID.fromString("b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10");

    private final RabbitTopologyConfig config = new RabbitTopologyConfig();
    private final JacksonJsonMessageConverter converter = converter();

    @Test
    void declaresQuorumWorkQueuesWithDeadLetterArgumentsAndTheirDlqs() {
        List<Queue> workQueues = workQueues();

        assertThat(workQueues).hasSize(6);
        for (Queue queue : workQueues) {
            assertThat(queue.isDurable()).as(queue.getName()).isTrue();
            assertThat(queue.isExclusive()).as(queue.getName()).isFalse();
            assertThat(queue.isAutoDelete()).as(queue.getName()).isFalse();
            assertThat(queue.getArguments())
                    .as(queue.getName())
                    .containsEntry("x-queue-type", "quorum")
                    .containsEntry("x-dead-letter-exchange", "")
                    .containsEntry("x-dead-letter-routing-key", AmqpNames.deadLetterQueue(queue.getName()));
        }

        List<Queue> dlqs = dlqs();
        assertThat(dlqs).hasSize(6);
        for (Queue dlq : dlqs) {
            assertThat(dlq.isDurable()).as(dlq.getName()).isTrue();
            assertThat(dlq.getArguments()).as(dlq.getName()).doesNotContainKey("x-queue-type");
        }
    }

    @Test
    void bindsEachWorkQueueToItsExchangeWithTheContractRoutingKey() {
        assertThat(config.reservationEventsExchange().getType()).isEqualTo(ExchangeTypes.TOPIC);
        assertThat(config.financeCommandsExchange().getType()).isEqualTo(ExchangeTypes.DIRECT);

        assertBinding(config.reservationInfoBinding(config.reservationInfoQueue(), config.reservationEventsExchange()),
                AmqpNames.EXCHANGE_EVENTS, AmqpNames.ROUTING_INFO_PROVIDED, AmqpNames.QUEUE_RESERVATION_INFO);
        assertBinding(
                config.reservationStatusBinding(config.reservationStatusQueue(), config.reservationEventsExchange()),
                AmqpNames.EXCHANGE_EVENTS, AmqpNames.ROUTING_STATUS_CHANGED, AmqpNames.QUEUE_RESERVATION_STATUS);
        assertBinding(
                config.guaranteeDisputeBinding(config.guaranteeDisputeQueue(), config.reservationEventsExchange()),
                AmqpNames.EXCHANGE_EVENTS, AmqpNames.ROUTING_DISPUTE_UPDATED, AmqpNames.QUEUE_GUARANTEE_DISPUTE);
        assertBinding(config.chargeCommandBinding(config.chargeCommandQueue(), config.financeCommandsExchange()),
                AmqpNames.EXCHANGE_COMMANDS, AmqpNames.ROUTING_CHARGE_COMMAND, AmqpNames.QUEUE_CHARGE_COMMAND);
        assertBinding(config.refundCommandBinding(config.refundCommandQueue(), config.financeCommandsExchange()),
                AmqpNames.EXCHANGE_COMMANDS, AmqpNames.ROUTING_REFUND_COMMAND, AmqpNames.QUEUE_REFUND_COMMAND);
        assertBinding(config.settlementCommandBinding(config.settlementCommandQueue(), config.financeCommandsExchange()),
                AmqpNames.EXCHANGE_COMMANDS, AmqpNames.ROUTING_SETTLEMENT_COMMAND, AmqpNames.QUEUE_SETTLEMENT_COMMAND);
    }

    @Test
    void serializesMessagesWithSnakeCaseAndDomainScalars() {
        SampleMessage body = new SampleMessage(new ReservationId(RESERVATION_UUID), new Money(new BigDecimal("350000")),
                new Percentage(new BigDecimal("21")), LocalDate.of(2026, 11, 1),
                Instant.parse("2026-11-01T10:15:30Z"));

        Message message = converter.toMessage(body, new MessageProperties());

        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"reservation_id\":\"" + RESERVATION_UUID + "\"")
                .contains("\"amount\":\"350000.00\"")
                .contains("\"percentage\":\"21.00\"")
                .contains("\"start_date\":\"2026-11-01\"")
                .contains("\"created_at\":\"2026-11-01T10:15:30Z\"");
    }

    @Test
    void deserializesSnakeCaseMessagesWithDomainScalars() {
        String json = """
                {"reservation_id":"%s","amount":"350000.00","percentage":"21.00","start_date":"2026-11-01",\
                "created_at":"2026-11-01T10:15:30Z"}""".formatted(RESERVATION_UUID);
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        Message message = new Message(json.getBytes(StandardCharsets.UTF_8), properties);

        SampleMessage parsed = (SampleMessage) converter.fromMessage(message, new ParameterizedTypeReference<SampleMessage>() {
        });

        assertThat(parsed.reservationId()).isEqualTo(new ReservationId(RESERVATION_UUID));
        assertThat(parsed.amount()).isEqualTo(new Money(new BigDecimal("350000")));
        assertThat(parsed.percentage()).isEqualTo(new Percentage(new BigDecimal("21")));
        assertThat(parsed.startDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(parsed.createdAt()).isEqualTo(Instant.parse("2026-11-01T10:15:30Z"));
    }

    private List<Queue> workQueues() {
        return List.of(config.reservationInfoQueue(), config.reservationStatusQueue(),
                config.guaranteeDisputeQueue(), config.chargeCommandQueue(), config.refundCommandQueue(),
                config.settlementCommandQueue());
    }

    private List<Queue> dlqs() {
        return List.of(config.reservationInfoDlq(), config.reservationStatusDlq(), config.guaranteeDisputeDlq(),
                config.chargeCommandDlq(), config.refundCommandDlq(), config.settlementCommandDlq());
    }

    private static void assertBinding(Binding binding, String exchange, String routingKey, String queue) {
        assertThat(binding.getExchange()).isEqualTo(exchange);
        assertThat(binding.getRoutingKey()).isEqualTo(routingKey);
        assertThat(binding.getDestination()).isEqualTo(queue);
    }

    private static JacksonJsonMessageConverter converter() {
        JsonMapper.Builder builder = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        new JacksonConfig().domainScalarsCustomizer().customize(builder);
        return new RabbitTopologyConfig().jsonMessageConverter(builder.build());
    }

    record SampleMessage(ReservationId reservationId, Money amount, Percentage percentage, LocalDate startDate,
                         Instant createdAt) {
    }
}
