package support.event.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InvInvCompSuccessEvent (
    String eventId,
    String sagaId,
    String stepId,
    String orderId,
    String inventoryId,
    String message
) {
}
