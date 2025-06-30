package com.geeson.geesonsaga.command.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaymentRequestPayload {
    private String orderId;
    private String userId;
    private String paymentId;
    private BigDecimal amount;
    private String paymentMethodId;
}