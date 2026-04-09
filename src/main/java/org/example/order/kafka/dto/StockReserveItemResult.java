package org.example.order.kafka.dto;

import java.util.UUID;

public record StockReserveItemResult(
        UUID productId,
        String sku,
        int requested,
        int reserved,
        boolean success,
        String reason
) {
}
