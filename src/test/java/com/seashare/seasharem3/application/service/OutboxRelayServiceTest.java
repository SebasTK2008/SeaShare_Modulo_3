package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.port.out.MessageBrokerPort;
import com.seashare.seasharem3.support.InMemoryOutbox;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class OutboxRelayServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T12:00:00Z"), ZoneOffset.UTC);

    private final InMemoryOutbox outbox = new InMemoryOutbox();
    private final RecordingBroker broker = new RecordingBroker();

    @Test
    void publicaYMarcaLosMensajesPendientesEnOrden() {
        outbox.publish("seashare.reservations", "reservation.info.provided", "{\"a\":1}");
        outbox.publish("seashare.finance.commands", "charge.command", "{\"b\":2}");
        OutboxRelayService service = new OutboxRelayService(outbox, broker, CLOCK, 100);

        int published = service.relay();

        assertThat(published).isEqualTo(2);
        assertThat(broker.published).extracting(PublishedMessage::routingKey)
                .containsExactly("reservation.info.provided", "charge.command");
        assertThat(outbox.pendingCount()).isZero();
        assertThat(outbox.publishedCount()).isEqualTo(2);
    }

    @Test
    void respetaElTamanoDeLoteEnCadaCiclo() {
        for (int i = 0; i < 5; i++) {
            outbox.publish("seashare.reservations", "reservation.status.changed", "{\"n\":" + i + "}");
        }
        OutboxRelayService service = new OutboxRelayService(outbox, broker, CLOCK, 2);

        assertThat(service.relay()).isEqualTo(2);
        assertThat(service.relay()).isEqualTo(2);
        assertThat(service.relay()).isEqualTo(1);
        assertThat(service.relay()).isZero();
        assertThat(broker.published).hasSize(5);
    }

    @Test
    void siElBrokerFallaNoMarcaPublicadoYPropagaLaExcepcion() {
        outbox.publish("seashare.reservations", "reservation.info.provided", "{\"a\":1}");
        broker.failing = true;
        OutboxRelayService service = new OutboxRelayService(outbox, broker, CLOCK, 100);

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(service::relay);

        assertThat(outbox.pendingCount()).isEqualTo(1);
        assertThat(outbox.publishedCount()).isZero();
    }

    private static final class RecordingBroker implements MessageBrokerPort {

        private final List<PublishedMessage> published = new ArrayList<>();
        private boolean failing;

        @Override
        public void publish(String exchange, String routingKey, String payload) {
            if (failing) {
                throw new IllegalStateException("broker caído");
            }
            published.add(new PublishedMessage(exchange, routingKey, payload));
        }
    }

    private record PublishedMessage(String exchange, String routingKey, String payload) {
    }
}
