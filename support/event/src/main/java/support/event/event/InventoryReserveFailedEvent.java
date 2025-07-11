package support.event.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryReserveFailedEvent (
    String eventId,
    String sagaId,
    String stepId,
    String orderId,
    String reason
) {
}