package com.seashare.seasharem3.infrastructure.adapter.out.messaging;

import com.seashare.seasharem3.application.dto.ChargeCommand;
import com.seashare.seasharem3.application.port.out.ChargeCommandPublisher;
import com.seashare.seasharem3.application.port.out.OutboxPort;
import com.seashare.seasharem3.infrastructure.config.amqp.AmqpNames;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
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
 *
 * <p>El puerto del outbox exige una transacción activa ({@code Propagation.MANDATORY}). Este
 * adaptador abre una transacción si el llamador no la tiene —el caso de la conciliación, que
 * republica un {@code PENDING_SEND} sin transacción previa— y se une a la del llamador cuando ya
 * existe, como en {@code ProcessChargeService}.</p>
 */
@Component
public class ChargeCommandOutboxAdapter implements ChargeCommandPublisher {

    private final OutboxPort outbox;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transactions;

    public ChargeCommandOutboxAdapter(OutboxPort outbox, JsonMapper jsonMapper,
            TransactionTemplate transactions) {
        this.outbox = outbox;
        this.jsonMapper = jsonMapper;
        this.transactions = transactions;
    }

    @Override
    public void publish(ChargeCommand command) {
        String payload = jsonMapper.writeValueAsString(command);
        transactions.executeWithoutResult(status ->
                outbox.publish(AmqpNames.EXCHANGE_COMMANDS, AmqpNames.ROUTING_CHARGE_COMMAND, payload));
    }
}
