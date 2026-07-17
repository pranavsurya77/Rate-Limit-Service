package com.example.rate_limit.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.rate_limit.dto.request.RateLimitRequest;
import com.example.rate_limit.dto.response.RateLimitResponse;
import com.example.rate_limit.model.RateLimitAlgorithm;
import com.example.rate_limit.service.TokenBucketService;
import com.example.rate_limit.service.SlidingWindowService;
import com.example.rate_limit.service.ClientService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/rate-limit")
@RequiredArgsConstructor
public class RateLimitController {

    private final TokenBucketService tokenBucketService;
    private final SlidingWindowService slidingWindowService;
    private final ClientService clientService;

    // Cache the algorithm locally for 30s to avoid database/Redis lookups on every
    // request
    private final ConcurrentHashMap<String, RateLimitAlgorithm> algorithmCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> cacheExpiry = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 30000; // 30 seconds

    @org.springframework.context.event.EventListener
    public void handleClientConfigChanged(com.example.rate_limit.event.ClientConfigChangedEvent event) {
        algorithmCache.remove(event.getClientKey());
        cacheExpiry.remove(event.getClientKey());
    }

    private RateLimitAlgorithm getClientAlgorithm(String clientKey) {
        long now = System.currentTimeMillis();
        RateLimitAlgorithm cached = algorithmCache.get(clientKey);
        Long expiry = cacheExpiry.get(clientKey);

        if (cached != null && expiry != null && expiry > now) {
            return cached;
        }

        RateLimitAlgorithm algo = RateLimitAlgorithm.TOKEN_BUCKET; // fallback default
        try {
            java.util.Optional<com.example.rate_limit.model.Client> clientOpt = clientService
                    .getClientByClientKey(clientKey);
            if (clientOpt.isPresent() && clientOpt.get().getAlgorithm() != null) {
                algo = clientOpt.get().getAlgorithm();
            }
        } catch (Exception e) {
            // Log or fallback
        }

        algorithmCache.put(clientKey, algo);
        cacheExpiry.put(clientKey, now + CACHE_TTL_MS);
        return algo;
    }

    @PostMapping("/check")
    public ResponseEntity<RateLimitResponse> isAllowed(@Valid @RequestBody RateLimitRequest request) {
        RateLimitAlgorithm algorithm = getClientAlgorithm(request.getClientKey());
        RateLimitResponse response;

        if (algorithm == RateLimitAlgorithm.SLIDING_WINDOW) {
            response = slidingWindowService.checkRateLimit(request.getClientKey(), request.getIdentifier());
        } else {
            response = tokenBucketService.checkRateLimit(request.getClientKey(), request.getIdentifier());
        }

        if (response.getDecision() == com.example.rate_limit.model.RateLimitDecision.DENY) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
        }
        return ResponseEntity.ok(response);
    }
}
