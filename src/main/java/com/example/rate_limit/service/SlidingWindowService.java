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
public class SlidingWindowService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisScript<List<Long>> slidingWindowScript;
    private final ClientService clientService;

    // Configurable defaults
    private static final int DEFAULT_MAX_TOKENS = 100;
    private static final int DEFAULT_WINDOW = 60000;

    @lombok.Value
    private static class ClientConfig {
        int maxTokens;
        int window;
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

        int maxRequests = DEFAULT_MAX_TOKENS;
        int window = DEFAULT_WINDOW;
        try {
            java.util.Optional<com.example.rate_limit.model.Client> clientOpt = clientService
                    .getClientByClientKey(clientKey);
            if (clientOpt.isPresent()) {
                com.example.rate_limit.model.Client client = clientOpt.get();
                maxRequests = client.getMaxTokens();
                window = client.getWindow();
            }
        } catch (Exception e) {
            // Log or fallback to default
        }

        ClientConfig config = new ClientConfig(maxRequests, window, now + CACHE_TTL_MS);
        configCache.put(clientKey, config);
        return config;
    }

    public RateLimitResponse checkRateLimit(String clientKey, String identifier) {
        ClientConfig config = getClientConfig(clientKey);

        List<Long> result = redisTemplate.execute(slidingWindowScript,
                List.of("rate_limit:window:" + clientKey + ":" + identifier, ""),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(config.getMaxTokens()),
                String.valueOf(config.getWindow()),
                java.util.UUID.randomUUID().toString());

        RateLimitResponse response = new RateLimitResponse();
        if (result != null && result.size() >= 5) {
            boolean allowed = result.get(0) == 1;
            response.setDecision(allowed ? RateLimitDecision.ALLOW : RateLimitDecision.DENY);
            response.setRemaining(result.get(1).intValue());
            response.setRetryAfterMs(result.get(2));
            response.setLimit(result.get(3).intValue());
            response.setResetAt(result.get(4));
        } else {
            response.setDecision(RateLimitDecision.DENY);
            response.setRemaining(0);
            response.setRetryAfterMs(0);
            response.setLimit(config.getMaxTokens());
            response.setResetAt(System.currentTimeMillis());
        }

        return response;
    }
}
