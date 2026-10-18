package com.seashare.seasharem3.infrastructure.config;

import com.seashare.seasharem3.application.port.out.MessageBrokerPort;
import com.seashare.seasharem3.application.port.out.OutboxMessageRepository;
import com.seashare.seasharem3.application.service.OutboxRelayService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Construye el servicio del relay del outbox manteniendo {@code application} libre de Spring
 * (regla general-plan §3.4-2). [CONV] núcleo compartido.
 */
@Configuration
public class OutboxConfig {

    @Bean
    public OutboxRelayService outboxRelayService(OutboxMessageRepository outboxMessageRepository,
            MessageBrokerPort messageBrokerPort, Clock clock,
            @Value("${seashare.outbox.batch-size:100}") int batchSize) {
        return new OutboxRelayService(outboxMessageRepository, messageBrokerPort, clock, batchSize);
    }
}
