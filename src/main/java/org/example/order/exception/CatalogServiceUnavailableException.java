package org.example.order.exception;

public class CatalogServiceUnavailableException extends RuntimeException {
    public CatalogServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
