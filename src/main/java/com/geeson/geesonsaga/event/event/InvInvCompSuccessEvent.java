package com.geeson.geesonsaga.event.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InvInvCompSuccessEvent (
    String eventId,
    String sagaId,
    String stepId,
    String orderId,
    String inventoryId,
    String message
) {
}
