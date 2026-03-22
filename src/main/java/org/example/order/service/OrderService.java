package org.example.order.service;

import lombok.RequiredArgsConstructor;
import org.example.order.dto.*;
import org.example.order.entity.Address;
import org.example.order.entity.Order;
import org.example.order.entity.OrderItem;
import org.example.order.enums.OrderStatus;
import org.example.order.exception.IllegalOrderStatusException;
import org.example.order.exception.OrderNotFoundException;
import org.example.order.repository.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        List<OrderItem> items = request.items().stream()
                .map(this::toOrderItem)
                .toList();

        BigDecimal total = items.stream()
                .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .userId(request.userId())
                .status(OrderStatus.CREATED)
                .totalAmount(total)
                .currency(request.currency() != null ? request.currency() : "EUR")
                .shippingAddress(toAddress(request.shippingAddress()))
                .build();

        items.forEach(item -> item.setOrder(order));
        order.getItems().addAll(items);

        return toResponse(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(UUID id) {
        return orderRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getByUser(UUID userId, OrderStatus status, Pageable pageable) {
        Page<Order> page = status != null
                ? orderRepository.findAllByUserIdAndStatus(userId, status, pageable)
                : orderRepository.findAllByUserId(userId, pageable);

        return page.map(this::toResponse);
    }

    @Transactional
    public OrderResponse cancelOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.PAID) {
            throw new IllegalOrderStatusException(
                    "Cannot cancel order in status: " + order.getStatus()
            );
        }

        order.setStatus(OrderStatus.CANCELLED);
        return toResponse(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse confirmPayment(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        if (order.getStatus() != OrderStatus.RESERVED) {
            throw new IllegalOrderStatusException(
                    "Cannot confirm payment for order in status: " + order.getStatus()
            );
        }

        order.setStatus(OrderStatus.PAID);
        return toResponse(orderRepository.save(order));
    }

    private OrderItem toOrderItem(OrderItemRequest req) {
        return OrderItem.builder()
                .productId(req.productId())
                .sku(req.sku())
                .productName(req.productName())
                .quantity(req.quantity())
                .price(req.price())
                .build();
    }

    private Address toAddress(AddressRequest req) {
        return Address.builder()
                .street(req.street())
                .city(req.city())
                .state(req.state())
                .zipCode(req.zipCode())
                .country(req.country())
                .build();
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(i -> new OrderItemResponse(
                        i.getId(), i.getProductId(), i.getSku(),
                        i.getProductName(), i.getQuantity(), i.getPrice()
                ))
                .toList();

        AddressRequest address = new AddressRequest(
                order.getShippingAddress().getStreet(),
                order.getShippingAddress().getCity(),
                order.getShippingAddress().getState(),
                order.getShippingAddress().getZipCode(),
                order.getShippingAddress().getCountry()
        );

        return new OrderResponse(
                order.getId(), order.getOrderNumber(), order.getUserId(),
                items, order.getTotalAmount(), order.getCurrency(),
                order.getStatus(), address, order.getCreatedAt(), order.getUpdatedAt()
        );
    }

    private String generateOrderNumber() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
