package org.example.order.controller;

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

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    @GetMapping("/{id}")
    public OrderResponse getById(@PathVariable UUID id) {
        return orderService.getById(id);
    }

    @GetMapping
    public Page<OrderResponse> getByUser(
            @RequestParam UUID userId,
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return orderService.getByUser(userId, status, pageable);
    }

    @PutMapping("/{id}/cancel")
    public OrderResponse cancelOrder(@PathVariable UUID id) {
        return orderService.cancelOrder(id);
    }

    @PostMapping("/{id}/confirm-payment")
    public OrderResponse confirmPayment(@PathVariable UUID id) {
        return orderService.confirmPayment(id);
    }
}
