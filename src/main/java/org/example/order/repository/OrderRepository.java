package org.example.order.repository;

import org.example.order.entity.Order;
import org.example.order.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByOrderNumber(String orderNumber);

    Page<Order> findAllByUserId(UUID userId, Pageable pageable);

    Page<Order> findAllByUserIdAndStatus(UUID userId, OrderStatus status, Pageable pageable);
}
