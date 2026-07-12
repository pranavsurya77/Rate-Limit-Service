package com.example.rate_limit.exception;

import com.example.rate_limit.dto.response.RateLimitResponse;
import lombok.Getter;

@Getter
public class RateLimitExceededException extends RuntimeException {
    private final RateLimitResponse response;

    public RateLimitExceededException(RateLimitResponse response) {
        super("Rate limit exceeded");
        this.response = response;
    }
}
