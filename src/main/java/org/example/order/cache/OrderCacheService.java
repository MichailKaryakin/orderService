package org.example.order.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.order.dto.OrderResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCacheService {

    private static final String ORDER_KEY_PREFIX = "order:";
    private static final Duration ORDER_TTL = Duration.ofHours(1);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public Optional<OrderResponse> getOrder(UUID id) {
        String key = ORDER_KEY_PREFIX + id;
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, OrderResponse.class));
        } catch (Exception e) {
            logCacheError("read", key, e);
            return Optional.empty();
        }
    }

    public void putOrder(OrderResponse order) {
        String key = ORDER_KEY_PREFIX + order.id();
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(order), ORDER_TTL);
        } catch (Exception e) {
            logCacheError("write", key, e);
        }
    }

    public void evictOrder(UUID id) {
        try {
            redisTemplate.delete(ORDER_KEY_PREFIX + id);
        } catch (Exception e) {
            logCacheError("evict", ORDER_KEY_PREFIX + id, e);
        }
    }

    private void logCacheError(String operation, String key, Exception e) {
        log.warn("Cache {} failed for key={}: {}", operation, key, e.getMessage());
    }
}
