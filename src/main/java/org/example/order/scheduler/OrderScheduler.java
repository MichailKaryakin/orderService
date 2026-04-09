package org.example.order.scheduler;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.order.cache.OrderCacheService;
import org.example.order.enums.OrderStatus;
import org.example.order.repository.OrderRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderScheduler {

    private final OrderRepository orderRepository;
    private final OrderCacheService cacheService;
    private final MeterRegistry meterRegistry;

    private final AtomicLong pendingOrders = new AtomicLong(0);
    private final AtomicLong failedOrders = new AtomicLong(0);

    @PostConstruct
    public void initGauges() {
        meterRegistry.gauge("order.status.pending", pendingOrders);
        meterRegistry.gauge("order.status.failed", failedOrders);
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cancelStaleOrders() {
        log.info("Checking for stale orders");
        try {
            Instant threshold = Instant.now().minus(15, ChronoUnit.MINUTES);
            var stale = orderRepository.findAllByStatusAndCreatedAtBefore(
                    OrderStatus.CREATED, threshold);

            if (stale.isEmpty()) {
                log.info("No stale orders found");
                return;
            }

            stale.forEach(order -> {
                order.setStatus(OrderStatus.CANCELLED);
                cacheService.evictOrder(order.getId());
                log.warn("Stale order cancelled: id={}, createdAt={}", order.getId(), order.getCreatedAt());
            });

            orderRepository.saveAll(stale);
            log.info("Cancelled {} stale orders", stale.size());
        } catch (Exception e) {
            log.error("Failed to cancel stale orders: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void recordOrderMetrics() {
        log.info("Recording order metrics");
        try {
            long pending = orderRepository.countByStatus(OrderStatus.CREATED);
            long failed = orderRepository.countByStatus(OrderStatus.FAILED);

            pendingOrders.set(pending);
            failedOrders.set(failed);

            log.info("Order metrics recorded: pending={}, failed={}", pending, failed);
        } catch (Exception e) {
            log.error("Failed to record order metrics: {}", e.getMessage());
        }
    }
}
