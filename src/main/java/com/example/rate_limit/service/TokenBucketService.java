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
    private final ClientService clientService;

    // Configurable defaults (can be overridden by client config)
    private static final int DEFAULT_MAX_TOKENS = 100;
    private static final int DEFAULT_REFILL_RATE = 10; // tokens per second

    @lombok.Value
    private static class ClientConfig {
        int maxTokens;
        int refillRate;
        long expiryTime;
    }

    private final java.util.concurrent.ConcurrentHashMap<String, ClientConfig> configCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 30000; // 30 seconds

    @org.springframework.context.event.EventListener
    public void handleClientConfigChanged(com.example.rate_limit.event.ClientConfigChangedEvent event) {
        configCache.remove(event.getClientKey());
    }

    private ClientConfig getClientConfig(String clientKey) {
        long now = System.currentTimeMillis();
        ClientConfig cached = configCache.get(clientKey);
        if (cached != null && cached.getExpiryTime() > now) {
            return cached;
        }

        int maxTokens = DEFAULT_MAX_TOKENS;
        int refillRate = DEFAULT_REFILL_RATE;
        try {
            java.util.Optional<com.example.rate_limit.model.Client> clientOpt = clientService
                    .getClientByClientKey(clientKey);
            if (clientOpt.isPresent()) {
                com.example.rate_limit.model.Client client = clientOpt.get();
                maxTokens = client.getMaxTokens();
                refillRate = client.getRefillRate();
            }
        } catch (Exception e) {
            // Log or fallback to default
        }

        ClientConfig config = new ClientConfig(maxTokens, refillRate, now + CACHE_TTL_MS);
        configCache.put(clientKey, config);
        return config;
    }

    /**
     * Attempts to consume a token from the bucket using Lua script.
     * 
     * @param clientKey The client identifier
     * @param consume   Number of tokens to consume (usually 1)
     * @return true if request is allowed, false otherwise
     */
    public RateLimitResponse checkRateLimit(String clientKey, String identifier) {
        ClientConfig config = getClientConfig(clientKey);

        // Convert arguments to String to avoid ClassCastException with
        // StringRedisSerializer
        List<Long> result = redisTemplate.execute(
                tokenBucketScript,
                List.of("rate_limit:bucket:" + clientKey + ":" + identifier, ""),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(config.getMaxTokens()),
                String.valueOf(config.getRefillRate()),
                "1" // Consume 1 token by default
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
            org.springframework.web.context.request.RequestAttributes attrs = org.springframework.web.context.request.RequestContextHolder
                    .getRequestAttributes();
            if (attrs != null) {
                attrs.setAttribute("rate-limit-limit", limit,
                        org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
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
        List<Object> state = redisTemplate.opsForHash()
                .multiGet("rate_limit:bucket:" + clientKey, List.of("tokens", "lastRefillTime"));
        if (state.size() >= 2 && state.get(0) != null && state.get(1) != null) {
            try {
                // Since we use StringRedisSerializer, the returned values are Strings
                String tokensStr = (String) state.get(0);
                String lastRefillTimeStr = (String) state.get(1);
                return java.util.Map.of(
                        "tokens", Double.parseDouble(tokensStr),
                        "lastRefillTime", Long.parseLong(lastRefillTimeStr));
            } catch (NumberFormatException e) {
                // Fallback to raw string values if parsing fails
                return java.util.Map.of(
                        "tokens", state.get(0),
                        "lastRefillTime", state.get(1));
            }
        }
        return java.util.Collections.emptyMap();
    }
}