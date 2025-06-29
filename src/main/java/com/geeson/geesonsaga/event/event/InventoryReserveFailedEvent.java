package com.geeson.geesonsaga.event.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReserveFailedEvent {
    private String sagaId;
    private String stepId;
    private String orderId;
    private String inventoryId;
    private String reason;
}