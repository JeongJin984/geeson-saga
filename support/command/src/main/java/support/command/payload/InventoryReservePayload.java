package support.command.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class InventoryReservePayload extends CommandPayload {
    private String reservationId;
    private String productId;
    private String orderId;
    private Integer quantity;
}
