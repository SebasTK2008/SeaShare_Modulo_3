package com.seashare.seasharem3.infrastructure.config.amqp;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Declara la topología congelada: exchange de eventos (topic) y sus tres colas consumidoras, más el
 * exchange interno de comandos (direct) con las colas de UC05/UC09/UC10. Todas las colas de trabajo
 * son quorum con DLQ; los reintentos de los listeners se configuran en application.properties.
 * Fuente: contracts/README §3.5 y §4.4; general-plan §7.2. [CONV] núcleo compartido.
 */
@Configuration
public class RabbitTopologyConfig {

    @Bean
    public TopicExchange reservationEventsExchange() {
        return new TopicExchange(AmqpNames.EXCHANGE_EVENTS, true, false);
    }

    @Bean
    public DirectExchange financeCommandsExchange() {
        return new DirectExchange(AmqpNames.EXCHANGE_COMMANDS, true, false);
    }

    @Bean
    public Queue reservationInfoQueue() {
        return quorum(AmqpNames.QUEUE_RESERVATION_INFO);
    }

    @Bean
    public Queue reservationStatusQueue() {
        return quorum(AmqpNames.QUEUE_RESERVATION_STATUS);
    }

    @Bean
    public Queue guaranteeDisputeQueue() {
        return quorum(AmqpNames.QUEUE_GUARANTEE_DISPUTE);
    }

    @Bean
    public Queue chargeCommandQueue() {
        return quorum(AmqpNames.QUEUE_CHARGE_COMMAND);
    }

    @Bean
    public Queue refundCommandQueue() {
        return quorum(AmqpNames.QUEUE_REFUND_COMMAND);
    }

    @Bean
    public Queue settlementCommandQueue() {
        return quorum(AmqpNames.QUEUE_SETTLEMENT_COMMAND);
    }

    @Bean
    public Queue reservationInfoDlq() {
        return dlq(AmqpNames.QUEUE_RESERVATION_INFO);
    }

    @Bean
    public Queue reservationStatusDlq() {
        return dlq(AmqpNames.QUEUE_RESERVATION_STATUS);
    }

    @Bean
    public Queue guaranteeDisputeDlq() {
        return dlq(AmqpNames.QUEUE_GUARANTEE_DISPUTE);
    }

    @Bean
    public Queue chargeCommandDlq() {
        return dlq(AmqpNames.QUEUE_CHARGE_COMMAND);
    }

    @Bean
    public Queue refundCommandDlq() {
        return dlq(AmqpNames.QUEUE_REFUND_COMMAND);
    }

    @Bean
    public Queue settlementCommandDlq() {
        return dlq(AmqpNames.QUEUE_SETTLEMENT_COMMAND);
    }

    @Bean
    public Binding reservationInfoBinding(@Qualifier("reservationInfoQueue") Queue queue,
            TopicExchange reservationEventsExchange) {
        return BindingBuilder.bind(queue).to(reservationEventsExchange).with(AmqpNames.ROUTING_INFO_PROVIDED);
    }

    @Bean
    public Binding reservationStatusBinding(@Qualifier("reservationStatusQueue") Queue queue,
            TopicExchange reservationEventsExchange) {
        return BindingBuilder.bind(queue).to(reservationEventsExchange).with(AmqpNames.ROUTING_STATUS_CHANGED);
    }

    @Bean
    public Binding guaranteeDisputeBinding(@Qualifier("guaranteeDisputeQueue") Queue queue,
            TopicExchange reservationEventsExchange) {
        return BindingBuilder.bind(queue).to(reservationEventsExchange).with(AmqpNames.ROUTING_DISPUTE_UPDATED);
    }

    @Bean
    public Binding chargeCommandBinding(@Qualifier("chargeCommandQueue") Queue queue,
            DirectExchange financeCommandsExchange) {
        return BindingBuilder.bind(queue).to(financeCommandsExchange).with(AmqpNames.ROUTING_CHARGE_COMMAND);
    }

    @Bean
    public Binding refundCommandBinding(@Qualifier("refundCommandQueue") Queue queue,
            DirectExchange financeCommandsExchange) {
        return BindingBuilder.bind(queue).to(financeCommandsExchange).with(AmqpNames.ROUTING_REFUND_COMMAND);
    }

    @Bean
    public Binding settlementCommandBinding(@Qualifier("settlementCommandQueue") Queue queue,
            DirectExchange financeCommandsExchange) {
        return BindingBuilder.bind(queue).to(financeCommandsExchange).with(AmqpNames.ROUTING_SETTLEMENT_COMMAND);
    }

    /**
     * Conversor JSON (Jackson 3) para listeners AMQP: contenido JSON ↔ DTOs de la aplicación.
     * [CONV] reutiliza el {@code JsonMapper} de Spring Boot (mismo que la API) para heredar
     * {@code spring.jackson.property-naming-strategy=SNAKE_CASE} y los escalares del dominio de
     * {@link com.seashare.seasharem3.infrastructure.config.web.JacksonConfig}; el mapper propio del
     * converter rompería la forma de los contratos y perdería {@code Money}/{@code Percentage}/IDs.
     */
    @Bean
    public JacksonJsonMessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }

    private static Queue quorum(String name) {
        return QueueBuilder.durable(name)
                .quorum()
                .deadLetterExchange("")
                .deadLetterRoutingKey(AmqpNames.deadLetterQueue(name))
                .build();
    }

    private static Queue dlq(String sourceQueue) {
        return QueueBuilder.durable(AmqpNames.deadLetterQueue(sourceQueue)).build();
    }
}
