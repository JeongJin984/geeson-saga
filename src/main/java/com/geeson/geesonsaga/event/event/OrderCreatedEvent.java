package com.geeson.geesonsaga.event.event;

import lombok.Getter;

@Getter
public class OrderCreatedEvent {
    private String sagaId;
    private Boolean restore;
}
