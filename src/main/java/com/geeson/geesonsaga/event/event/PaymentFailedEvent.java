package com.geeson.geesonsaga.event.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedEvent {
    private String sagaId;
    private String stepId;
    private String orderId;
    private String paymentId;
    private String reason;
}