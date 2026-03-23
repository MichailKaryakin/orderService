package org.example.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("Creating order for userId={}, items={}", request.userId(), request.items().size());

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

        OrderResponse response = toResponse(orderRepository.save(order));
        log.info("Order created: id={}, number={}, total={} {}",
                response.id(), response.orderNumber(), response.totalAmount(), response.currency());
        return response;
    }

    public OrderResponse getById(UUID id) {
        log.debug("Fetching order by id={}", id);
        return orderRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> {
                    logOrderNotFound(id);
                    return new OrderNotFoundException(id);
                });
    }

    public Page<OrderResponse> getByUser(UUID userId, OrderStatus status, Pageable pageable) {
        log.debug("Fetching orders for userId={}, status={}, page={}", userId, status, pageable.getPageNumber());

        Page<Order> page = status != null
                ? orderRepository.findAllByUserIdAndStatus(userId, status, pageable)
                : orderRepository.findAllByUserId(userId, pageable);

        return page.map(this::toResponse);
    }

    @Transactional
    public OrderResponse cancelOrder(UUID id) {
        log.info("Cancelling order id={}", id);

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> {
                    logOrderNotFound(id);
                    return new OrderNotFoundException(id);
                });

        if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.PAID) {
            log.warn("Cannot cancel order id={}, current status={}", id, order.getStatus());
            throw new IllegalOrderStatusException(
                    "Cannot cancel order in status: " + order.getStatus()
            );
        }

        order.setStatus(OrderStatus.CANCELLED);
        OrderResponse response = toResponse(orderRepository.save(order));
        log.info("Order cancelled: id={}", id);
        return response;
    }

    @Transactional
    public OrderResponse confirmPayment(UUID id) {
        log.info("Confirming payment for order id={}", id);

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> {
                    logOrderNotFound(id);
                    return new OrderNotFoundException(id);
                });

        if (order.getStatus() != OrderStatus.RESERVED) {
            log.warn("Cannot confirm payment for order id={}, current status={}", id, order.getStatus());
            throw new IllegalOrderStatusException(
                    "Cannot confirm payment for order in status: " + order.getStatus()
            );
        }

        order.setStatus(OrderStatus.PAID);
        OrderResponse response = toResponse(orderRepository.save(order));
        log.info("Payment confirmed: id={}, new status=PAID", id);
        return response;
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

    private static void logOrderNotFound(UUID id) {
        log.warn("Order not found: id={}", id);
    }
}
