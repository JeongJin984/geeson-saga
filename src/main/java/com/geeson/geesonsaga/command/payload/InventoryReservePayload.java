package com.geeson.geesonsaga.command.payload;

public record InventoryReservePayload (
    String userId,
    String productId,
    Integer quantity
) {
}
