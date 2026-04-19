package org.example.order.client;

import lombok.extern.slf4j.Slf4j;
import org.example.order.exception.CatalogServiceUnavailableException;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CatalogClientFallbackFactory implements FallbackFactory<CatalogClient> {

    @Override
    public CatalogClient create(Throwable cause) {
        log.error("CatalogClient fallback triggered: {}", cause.getMessage());
        return id -> {
            throw new CatalogServiceUnavailableException("Catalog unavailable: getStock failed", cause);
        };
    }
}
