package org.example.order.kafka.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StockReserveResultEvent(
        String eventType,
        UUID orderId,
        List<StockReserveItemResult> results,
        Instant timestamp
) {
}
