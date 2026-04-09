package org.example.order.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.order.kafka.dto.OrderStockReserveEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final KafkaTemplate<String, OrderStockReserveEvent> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderEvent(OrderStockReserveEvent event) {
        kafkaTemplate.send("order.stock.reserve", event.orderId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send order.stock.reserve for orderId={}: {}",
                                event.orderId(), ex.getMessage());
                    } else {
                        log.info("Sent order.stock.reserve for orderId={}", event.orderId());
                    }
                });
    }
}
