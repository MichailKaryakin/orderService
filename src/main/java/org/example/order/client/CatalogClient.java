package org.example.order.client;

import org.example.order.client.dto.StockResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@FeignClient(
        name = "catalog-service",
        url = "${catalog.service.url}",
        fallbackFactory = CatalogClientFallbackFactory.class
)
public interface CatalogClient {

    @GetMapping("/api/v1/products/{id}/stock")
    StockResponse getStock(@PathVariable UUID id);
}
