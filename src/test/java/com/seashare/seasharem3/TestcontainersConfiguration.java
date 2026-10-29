package com.seashare.seasharem3;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Contenedores de integración compartidos. El timeout de arranque se amplía porque RabbitMQ levanta
 * en más de un minuto en máquinas con poca memoria o con la CPU compartida con Maven, y el valor
 * por defecto (60 s) producía fallos intermitentes de contexto.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(3);

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:16"))
				.withStartupTimeout(STARTUP_TIMEOUT);
	}

	@Bean
	@ServiceConnection
	RabbitMQContainer rabbitContainer() {
		return new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"))
				.withStartupTimeout(STARTUP_TIMEOUT);
	}

}
