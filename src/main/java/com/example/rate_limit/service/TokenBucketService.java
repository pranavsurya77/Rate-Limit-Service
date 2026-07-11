package com.example.rate_limit.service;

import java.util.List;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TokenBucketService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisScript<List<Long>> tokenBucketScript;
    // Configurable defaults (can be overridden by client config)
    private static final int DEFAULT_MAX_TOKENS = 100;
    private static final int DEFAULT_REFILL_RATE = 10; // tokens per second

    /**
     * Attempts to consume a token from the bucket using Lua script.
     * 
     * @param clientKey The client identifier
     * @param consume   Number of tokens to consume (usually 1)
     * @return true if request is allowed, false otherwise
     */
    public boolean tryConsume(String clientKey, int consume) {
        List<Long> result = redisTemplate.execute(
                tokenBucketScript,
                List.of("rate_limit:bucket:" + clientKey, "clients:" + clientKey),
                System.currentTimeMillis(),
                DEFAULT_MAX_TOKENS,
                DEFAULT_REFILL_RATE,
                consume);
        if (result != null && !result.isEmpty()) {
            long allowed = result.get(0);
            return allowed == 1;
        }
        return false;
    }

    /**
     * Gets current token bucket state.
     * 
     * @param clientKey The client identifier
     * @return Map with token info or empty map if not found
     */
    public java.util.Map<String, Object> getBucketState(String clientKey) {
        var state = redisTemplate.opsForHash()
                .multiGet("rate_limit:bucket:" + clientKey, List.of("tokens", "lastRefillTime"));
        if (state.get(0) != null && state.get(1) != null) {
            return java.util.Map.of(
                    "tokens", state.get(0),
                    "lastRefillTime", state.get(1));
        }
        return java.util.Collections.emptyMap();
    }
}