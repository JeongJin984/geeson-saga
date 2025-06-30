package com.geeson.geesonsaga.command.payload;

public record PayInvCompPayload (
    String orderId,
    String paymentId
) {
}
