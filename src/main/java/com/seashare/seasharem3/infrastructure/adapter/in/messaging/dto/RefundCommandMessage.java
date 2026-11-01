package com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto;

import java.util.UUID;

public record RefundCommandMessage(UUID refundIntentId) {
}
