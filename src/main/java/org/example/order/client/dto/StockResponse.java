package org.example.order.client.dto;

import java.util.UUID;

public record StockResponse(
        UUID id,
        UUID productId,
        int quantity,
        int reserved,
        String warehouseLocation
) {
}
