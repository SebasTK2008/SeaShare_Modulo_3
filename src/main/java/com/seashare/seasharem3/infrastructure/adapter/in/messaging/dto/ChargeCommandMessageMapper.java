package com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto;

import com.seashare.seasharem3.application.dto.ChargeCommand;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Traduce el mensaje de la cola interna al comando de aplicación del worker de UC05.
 * Fuente: UC05 plan T014; precedente de UC03 (mapper del mensaje al comando).
 */
@Component
public class ChargeCommandMessageMapper {

    public ChargeCommand toCommand(ChargeCommandMessage message) {
        Objects.requireNonNull(message, "message");
        return new ChargeCommand(message.chargeIntentId(), message.payerEmail());
    }
}
