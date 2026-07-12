package com.example.rate_limit.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.example.rate_limit.dto.response.RateLimitResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<RateLimitResponse> handleRateLimitExceeded(RateLimitExceededException ex) {
        RateLimitResponse response = ex.getResponse();
        
        long retryAfterSec = (long) Math.ceil(response.getRetryAfterMs() / 1000.0);
        long resetAtEpochSec = (long) Math.ceil(response.getResetAt() / 1000.0);

        String limit = "100"; // default fallback
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            Object limitAttr = attrs.getAttribute("rate-limit-limit", ServletRequestAttributes.SCOPE_REQUEST);
            if (limitAttr != null) {
                limit = limitAttr.toString();
            }
        }

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("X-RateLimit-Limit", limit)
                .header("X-RateLimit-Remaining", String.valueOf(response.getRemaining()))
                .header("X-RateLimit-Reset", String.valueOf(resetAtEpochSec))
                .header("Retry-After", String.valueOf(retryAfterSec))
                .body(response);
    }
}
