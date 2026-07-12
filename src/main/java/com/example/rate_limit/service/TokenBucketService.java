package com.example.rate_limit.service;

import java.util.List;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import com.example.rate_limit.dto.response.RateLimitResponse;
import com.example.rate_limit.model.RateLimitDecision;

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
    public RateLimitResponse checkRateLimit(String clientKey, String identifier) {
        List<Long> result = redisTemplate.execute(
                tokenBucketScript,
                List.of("rate_limit:bucket:" + clientKey + ":" + identifier, "clients:" + clientKey),
                System.currentTimeMillis(),
                DEFAULT_MAX_TOKENS,
                DEFAULT_REFILL_RATE,
                1 // Consume 1 token by default
        );

        RateLimitResponse response = new RateLimitResponse();
        if (result != null && result.size() >= 5) {
            boolean allowed = result.get(0) == 1;
            response.setDecision(allowed ? RateLimitDecision.ALLOW : RateLimitDecision.DENY);
            response.setRemaining(result.get(1).intValue());
            response.setRetryAfterMs(result.get(2));
            response.setResetAt(result.get(4));

            // Set limit in request attributes to pass to interceptors/advice
            int limit = result.get(3).intValue();
            org.springframework.web.context.request.RequestAttributes attrs = 
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                attrs.setAttribute("rate-limit-limit", limit, org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
            }
        } else {
            // Fallback in case script execution fails
            response.setDecision(RateLimitDecision.DENY);
            response.setRemaining(0);
            response.setRetryAfterMs(0);
            response.setResetAt(System.currentTimeMillis());
        }
        return response;
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