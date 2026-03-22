package org.example.order.dto;

import org.example.order.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String orderNumber,
        UUID userId,
        List<OrderItemResponse> items,
        BigDecimal totalAmount,
        String currency,
        OrderStatus status,
        AddressRequest shippingAddress,
        Instant createdAt,
        Instant updatedAt
) {
}
