package com.example.rate_limit.dto.request;

import jakarta.validation.constraints.NotBlank;

import lombok.Data;

@Data
public class RateLimitRequest {

    @NotBlank(message = "ClientKey must not be blank")
    private String clientKey;

    @NotBlank(message = "identifier must note be blank")
    private String identifier;
}
