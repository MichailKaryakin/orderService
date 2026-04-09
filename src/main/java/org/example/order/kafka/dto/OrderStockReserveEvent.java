package org.example.order.kafka.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderStockReserveEvent(
        String eventType,
        UUID orderId,
        UUID userId,
        List<OrderStockItem> items,
        Instant timestamp
) {
    public static OrderStockReserveEvent of(UUID orderId, UUID userId, List<OrderStockItem> items) {
        return new OrderStockReserveEvent("ORDER_STOCK_RESERVE", orderId, userId, items, Instant.now());
    }
}
