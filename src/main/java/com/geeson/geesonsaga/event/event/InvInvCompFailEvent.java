package com.geeson.geesonsaga.event.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class InvInvCompFailEvent {
    private String sagaId;
    private String stepId;
    private String orderId;
    private String inventoryId;
}
