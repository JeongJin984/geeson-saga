package com.geeson.geesonsaga.command.payload;

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
public class InvDecPayload extends CommandPayload {
    private String inventoryId;
    private String productId;
    private int quantity;
}
