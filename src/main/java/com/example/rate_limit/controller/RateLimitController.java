package com.example.rate_limit.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.rate_limit.dto.request.RateLimitRequest;
import com.example.rate_limit.dto.response.RateLimitResponse;
import com.example.rate_limit.service.TokenBucketService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/rate-limit")
@RequiredArgsConstructor
public class RateLimitController {

    private final TokenBucketService tokenBucketService;

    @PostMapping("/check")
    public ResponseEntity<RateLimitResponse> isAllowed(@Valid @RequestBody RateLimitRequest request) {
        RateLimitResponse response = tokenBucketService.checkRateLimit(request.getClientKey(), request.getIdentifier());
        if (response.getDecision() == com.example.rate_limit.model.RateLimitDecision.DENY) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
        }
        return ResponseEntity.ok(response);
    }
}
