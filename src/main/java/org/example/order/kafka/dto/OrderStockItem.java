package org.example.order.kafka.dto;

import java.util.UUID;

public record OrderStockItem(
        UUID productId,
        String sku,
        int quantity
) {
}
