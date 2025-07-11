package support.event.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentFailedEvent (
    String eventId,
    String sagaId,
    String stepId,
    String orderId,
    String paymentId,
    String reason
) {
}