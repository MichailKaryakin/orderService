package org.example.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.order.dto.CreateOrderRequest;
import org.example.order.dto.OrderResponse;
import org.example.order.enums.OrderStatus;
import org.example.order.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Orders", description = "Managing orders and payments")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "Create order", description = "Creates a new order and publishes stock reserve event to Kafka")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created"),
            @ApiResponse(responseCode = "400", description = "Validation error")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    @Operation(summary = "Get order by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order found"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    @GetMapping("/{id}")
    public OrderResponse getById(@Parameter(description = "Order UUID") @PathVariable UUID id) {
        return orderService.getById(id);
    }

    @Operation(summary = "Get orders by user", description = "Returns paginated list of orders for a user, optionally filtered by status")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of orders")
    })
    @GetMapping
    public Page<OrderResponse> getByUser(
            @Parameter(description = "User UUID") @RequestParam UUID userId,
            @Parameter(description = "Filter by order status") @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return orderService.getByUser(userId, status, pageable);
    }

    @Operation(summary = "Cancel order", description = "Cancels an order. Not allowed for PAID or SHIPPED orders")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order cancelled"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "422", description = "Cannot cancel order in current status")
    })
    @PutMapping("/{id}/cancel")
    public OrderResponse cancelOrder(@Parameter(description = "Order UUID") @PathVariable UUID id) {
        return orderService.cancelOrder(id);
    }

    @Operation(summary = "Confirm payment", description = "Confirms payment for a RESERVED order, moves it to PAID status")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment confirmed"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "422", description = "Cannot confirm payment in current status")
    })
    @PostMapping("/{id}/confirm-payment")
    public OrderResponse confirmPayment(@Parameter(description = "Order UUID") @PathVariable UUID id) {
        return orderService.confirmPayment(id);
    }
}
