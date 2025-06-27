package com.geeson.geesonsaga.enums;

public enum OrderSagaState {
    ORDER_CREATED,
    PAYMENT_REQUESTED,
    PAYMENT_COMPLETED,
    INVENTORY_RESERVED,
    ORDER_COMPLETED,
    COMPENSATING, FAILED
}