package com.example.rate_limit.dto.response;

import com.example.rate_limit.model.RateLimitDecision;

import lombok.Data;

@Data
public class RateLimitResponse {

    private RateLimitDecision decision;
    private int remaining;
    private long retryAfterMs;
    private long resetAt;
}
