package com.geeson.geesonsaga.event.event;

import lombok.Getter;

@Getter
public class InvInvCompSuccessEvent {
    private String sagaId;
    private String stepId;
    private String orderId;
    private String inventoryId;
    private String message;
}
