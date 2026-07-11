package com.example.rate_limit.dto.request;

import com.example.rate_limit.model.RateLimitAlgorithm;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ClientConfigRequest {

    @NotBlank(message = "clientKey must not be blank")
    private String clientKey;

    @NotNull(message = "maxTokens is required")
    @Min(value = 1, message = "maxTokens must be at least 1")
    private Integer maxTokens;

    @NotNull(message = "refillRate is required")
    @Min(value = 1, message = "refillRate must be at least 1")
    private Integer refillRate;

    @NotNull(message = "algorithm is required")
    private RateLimitAlgorithm algorithm;

}
