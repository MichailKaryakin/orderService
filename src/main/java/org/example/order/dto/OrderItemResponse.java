package org.example.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        UUID productId,
        String sku,
        String productName,
        int quantity,
        BigDecimal price
) {
}
