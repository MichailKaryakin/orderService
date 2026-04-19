package org.example.order.exception;

import java.util.UUID;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(UUID productId, String sku, int quantity) {
        super("Insufficient stock for product: " + productId + ", sku: " + sku + ", requested quantity: " + quantity);
    }
}
