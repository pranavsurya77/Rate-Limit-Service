package com.example.rate_limit.dto.response;

import java.time.Instant;

import com.example.rate_limit.model.RateLimitAlgorithm;

import lombok.Data;

@Data
public class ClientConfigResponse {

    private String clientId;
    private String clientKey;
    private Integer maxTokens;
    private Integer refillRate;
    private RateLimitAlgorithm algorithm;
    private Instant createdAt;
    private Instant updatedAt;

}
