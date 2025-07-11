package support.event.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCreatedEvent (
    String orderId,
    String customerId,

    String paymentMethodId,
    String transactionId,
    BigDecimal totalPrice,
    String currency,
    List<OrderItem> items


) {
    public record OrderItem(
        String productId,
        int quantity,
        BigDecimal unitPrice
    ) {}
}
