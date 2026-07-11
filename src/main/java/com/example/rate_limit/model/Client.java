package com.example.rate_limit.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RedisHash("clients")
public class Client {
    @Id
    private String id;

    private String clientKey;
    private int maxTokens;
    private int refillRate;

    private RateLimitAlgorithm algorithm;

    private Instant createdAt;
    private Instant updatedAt;

}
