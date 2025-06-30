package com.geeson.geesonsaga.command.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentRequestPayload extends CommandPayload {
    private String orderId;
    private String userId;
    private String paymentId;
    private BigDecimal amount;
    private String paymentMethodId;
    private String currency;
}