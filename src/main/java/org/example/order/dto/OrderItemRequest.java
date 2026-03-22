package org.example.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemRequest(

        @NotNull
        UUID productId,

        @NotBlank
        String sku,

        @NotBlank
        String productName,

        @Positive
        int quantity,

        @NotNull
        @Positive
        BigDecimal price
) {
}
