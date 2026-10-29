package com.seashare.seasharem3.infrastructure.adapter.out.messaging;

import com.seashare.seasharem3.application.dto.ChargeCommand;
import com.seashare.seasharem3.application.port.out.ChargeCommandPublisher;
import com.seashare.seasharem3.application.port.out.OutboxPort;
import com.seashare.seasharem3.infrastructure.config.amqp.AmqpNames;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publica el comando de cobro en la cola interna de UC05 mediante el outbox transaccional del
 * núcleo: el mensaje queda en {@code outbox_message} dentro de la transacción del llamador y el
 * relay lo entrega al broker después (general-plan D-08).
 *
 * <p>El cuerpo es el JSON del comando con la forma snake_case de los contratos
 * ({@code charge_intent_id}, {@code payer_email}); el token del medio de pago no viaja en el
 * mensaje, el worker lo lee de {@code charge_intent} (CE-005).</p>
 *
 * <p>Fuente: UC05 plan T008 y T014; contracts/README §3.1 y §3.5.</p>
 */
@Component
public class ChargeCommandOutboxAdapter implements ChargeCommandPublisher {

    private final OutboxPort outbox;
    private final JsonMapper jsonMapper;

    public ChargeCommandOutboxAdapter(OutboxPort outbox, JsonMapper jsonMapper) {
        this.outbox = outbox;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void publish(ChargeCommand command) {
        outbox.publish(AmqpNames.EXCHANGE_COMMANDS, AmqpNames.ROUTING_CHARGE_COMMAND,
                jsonMapper.writeValueAsString(command));
    }
}
