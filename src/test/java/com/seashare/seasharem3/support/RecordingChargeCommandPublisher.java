package com.seashare.seasharem3.support;

import com.seashare.seasharem3.application.dto.ChargeCommand;
import com.seashare.seasharem3.application.port.out.ChargeCommandPublisher;

import java.util.ArrayList;
import java.util.List;

/**
 * Fake en memoria de {@link ChargeCommandPublisher} que registra los comandos publicados.
 * [CONV] núcleo compartido para las pruebas de UC05.
 */
public class RecordingChargeCommandPublisher implements ChargeCommandPublisher {

    private final List<ChargeCommand> published = new ArrayList<>();

    @Override
    public void publish(ChargeCommand command) {
        published.add(command);
    }

    public List<ChargeCommand> published() {
        return List.copyOf(published);
    }

    public int publishedCount() {
        return published.size();
    }
}
