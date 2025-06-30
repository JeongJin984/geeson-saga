package com.geeson.geesonsaga.event.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryReserveFailedEvent (
    String eventId,
    String sagaId,
    String stepId,
    String orderId,
    String inventoryId,
    String reason
) {
}