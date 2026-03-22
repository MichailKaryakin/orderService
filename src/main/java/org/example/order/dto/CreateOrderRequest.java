package org.example.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(

        @NotNull
        UUID userId,

        @NotEmpty
        @Valid
        List<OrderItemRequest> items,

        @NotNull
        @Valid
        AddressRequest shippingAddress,

        String currency
) {
}
