package org.example.order.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.order.kafka.dto.StockReserveResultEvent;
import org.example.order.service.OrderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockReserveResultConsumer {

    private final OrderService orderService;

    @KafkaListener(topics = "stock.reserve.result", groupId = "order-stock-result")
    public void handle(StockReserveResultEvent event) {
        log.info("Received stock.reserve.result for orderId={}", event.orderId());

        boolean allSuccess = event.results().stream().allMatch(r -> r.success());

        if (allSuccess) {
            orderService.markAsReserved(event.orderId());
        } else {
            String reasons = event.results().stream()
                    .filter(r -> !r.success())
                    .map(r -> r.sku() + ": " + r.reason())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("unknown");
            log.warn("Stock reserve failed for orderId={}, reasons: {}", event.orderId(), reasons);
            orderService.markAsFailed(event.orderId());
        }
    }
}
