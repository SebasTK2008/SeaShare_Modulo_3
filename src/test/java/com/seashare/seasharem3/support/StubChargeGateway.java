package com.seashare.seasharem3.support;

import com.seashare.seasharem3.application.dto.ChargeGatewayRequest;
import com.seashare.seasharem3.application.dto.ChargeGatewayResult;
import com.seashare.seasharem3.application.port.out.ChargeGatewayPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Doble en memoria de la pasarela de pago: registra las solicitudes enviadas y devuelve el
 * resultado configurado. [CONV] núcleo compartido para las pruebas de UC05/UC09/UC10.
 */
public class StubChargeGateway implements ChargeGatewayPort {

    private final List<ChargeGatewayRequest> requests = new ArrayList<>();

    private ChargeGatewayResult sendResult;
    private RuntimeException sendFailure;
    private ChargeGatewayResult fetchResult;
    private RuntimeException fetchFailure;

    @Override
    public ChargeGatewayResult send(ChargeGatewayRequest request) {
        requests.add(request);
        if (sendFailure != null) {
            throw sendFailure;
        }
        return sendResult;
    }

    @Override
    public Optional<ChargeGatewayResult> fetchPayment(String externalReference) {
        if (fetchFailure != null) {
            throw fetchFailure;
        }
        return Optional.ofNullable(fetchResult);
    }

    public StubChargeGateway withSendResult(ChargeGatewayResult result) {
        this.sendResult = result;
        return this;
    }

    public StubChargeGateway withSendFailure(RuntimeException failure) {
        this.sendFailure = failure;
        return this;
    }

    public StubChargeGateway withFetchResult(ChargeGatewayResult result) {
        this.fetchResult = result;
        return this;
    }

    public StubChargeGateway withFetchFailure(RuntimeException failure) {
        this.fetchFailure = failure;
        return this;
    }

    public List<ChargeGatewayRequest> requests() {
        return List.copyOf(requests);
    }

    public ChargeGatewayRequest lastRequest() {
        return requests.get(requests.size() - 1);
    }
}
