# Token Bucket Rate Limiter Service — Phase 1: Interceptors & Exception Handling

This plan outlines the implementation of Requirement #06 (Rate-limiting headers) and Requirement #01 (ALLOW/DENY mapping) using a clean Exception-driven and Controller Advice-driven pattern in Spring Boot.

---

## Proposed Design

To return standard rate-limiting headers on *every* response (both successful and rate-limited) without cluttering the main controller logic:

1. **Custom Exceptions**:
   - [RateLimitExceededException](file:///c:/Users/pranav/wd/project/rate/rate_limit/src/main/java/com/example/rate_limit/exception/RateLimitExceededException.java): Thrown when a request is denied. Holds the associated `RateLimitResponse`.

2. **Global Exception Handler**:
   - [GlobalExceptionHandler](file:///c:/Users/pranav/wd/project/rate/rate_limit/src/main/java/com/example/rate_limit/exception/GlobalExceptionHandler.java): Catches `RateLimitExceededException`, returns HTTP Status `429 (Too Many Requests)`, sets rate-limiting headers, and returns the response body.

3. **Controller Response Advice**:
   - [RateLimitHeaderAdvice](file:///c:/Users/pranav/wd/project/rate/rate_limit/src/main/java/com/example/rate_limit/interceptor/RateLimitHeaderAdvice.java): Implements `ResponseBodyAdvice<RateLimitResponse>` to intercept `200 OK` rate-limit responses and set headers before serialization.

---

## Detailed Changes

### 1. Lua Script Updates

#### [MODIFY] [token_bucket.lua](file:///c:/Users/pranav/wd/project/rate/rate_limit/src/main/resources/scripts/token_bucket.lua)
Update the return statement to include `max_tokens` (limit) and calculate `reset_at_epoch_ms` so the Java service doesn't have to guess or do extra lookups.

```lua
-- Calculate reset time (when bucket refills fully)
local reset_after_ms = math.ceil(((max_tokens - current_tokens) / refill_rate) * 1000)
local reset_at_epoch_ms = now + reset_after_ms

return { allowed, math.floor(remaining), retry_after_ms, max_tokens, reset_at_epoch_ms }
```

---

### 2. DTO & Service Updates

#### [MODIFY] [RateLimitResponse.java](file:///c:/Users/pranav/wd/project/rate/rate_limit/src/main/java/com/example/rate_limit/dto/response/RateLimitResponse.java)
Add `limit` and `resetAtEpochMs` fields.

```java
package com.example.rate_limit.dto.response;

import com.example.rate_limit.model.RateLimitDecision;
import lombok.Data;

@Data
public class RateLimitResponse {
    private RateLimitDecision decision;
    private int remaining;
    private long retryAfterMs;
    private int limit;
    private long resetAtEpochMs;
}
```

#### [MODIFY] [TokenBucketService.java](file:///c:/Users/pranav/wd/project/rate/rate_limit/src/main/java/com/example/rate_limit/service/TokenBucketService.java)
Update return parsing to read the new fields from the Lua script response.

```java
        RateLimitResponse response = new RateLimitResponse();
        if (result != null && result.size() >= 5) {
            boolean allowed = result.get(0) == 1;
            response.setDecision(allowed ? RateLimitDecision.ALLOW : RateLimitDecision.DENY);
            response.setRemaining(result.get(1).intValue());
            response.setRetryAfterMs(result.get(2));
            response.setLimit(result.get(3).intValue());
            response.setResetAtEpochMs(result.get(4));
        } else {
            response.setDecision(RateLimitDecision.DENY);
            response.setRemaining(0);
            response.setRetryAfterMs(0);
            response.setLimit(DEFAULT_MAX_TOKENS);
            response.setResetAtEpochMs(System.currentTimeMillis());
        }
```

---

### 3. Exception Handling

#### [NEW] [RateLimitExceededException.java](file:///c:/Users/pranav/wd/project/rate/rate_limit/src/main/java/com/example/rate_limit/exception/RateLimitExceededException.java)
```java
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
```

#### [NEW] [GlobalExceptionHandler.java](file:///c:/Users/pranav/wd/project/rate/rate_limit/src/main/java/com/example/rate_limit/exception/GlobalExceptionHandler.java)
```java
package com.example.rate_limit.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.rate_limit.dto.response.RateLimitResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<RateLimitResponse> handleRateLimitExceeded(RateLimitExceededException ex) {
        RateLimitResponse response = ex.getResponse();
        
        long retryAfterSec = (long) Math.ceil(response.getRetryAfterMs() / 1000.0);
        long resetAtEpochSec = (long) Math.ceil(response.getResetAtEpochMs() / 1000.0);

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("X-RateLimit-Limit", String.valueOf(response.getLimit()))
                .header("X-RateLimit-Remaining", String.valueOf(response.getRemaining()))
                .header("X-RateLimit-Reset", String.valueOf(resetAtEpochSec))
                .header("Retry-After", String.valueOf(retryAfterSec))
                .body(response);
    }
}
```

---

### 4. Controller Advice for Successful Responses

#### [NEW] [RateLimitHeaderAdvice.java](file:///c:/Users/pranav/wd/project/rate/rate_limit/src/main/java/com/example/rate_limit/interceptor/RateLimitHeaderAdvice.java)
```java
package com.example.rate_limit.interceptor;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import com.example.rate_limit.dto.response.RateLimitResponse;

@RestControllerAdvice
public class RateLimitHeaderAdvice implements ResponseBodyAdvice<RateLimitResponse> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return returnType.getParameterType().equals(RateLimitResponse.class);
    }

    @Override
    public RateLimitResponse beforeBodyWrite(RateLimitResponse body, MethodParameter returnType,
            MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request, ServerHttpResponse response) {
        
        if (body != null) {
            long resetAtEpochSec = (long) Math.ceil(body.getResetAtEpochMs() / 1000.0);
            response.getHeaders().add("X-RateLimit-Limit", String.valueOf(body.getLimit()));
            response.getHeaders().add("X-RateLimit-Remaining", String.valueOf(body.getRemaining()));
            response.getHeaders().add("X-RateLimit-Reset", String.valueOf(resetAtEpochSec));
        }
        return body;
    }
}
```

---

### 5. Controller Changes

#### [MODIFY] [RateLimitController.java](file:///c:/Users/pranav/wd/project/rate/rate_limit/src/main/java/com/example/rate_limit/controller/RateLimitController.java)
Modify the endpoint to throw `RateLimitExceededException` on DENY.

```java
    @PostMapping("/check")
    public RateLimitResponse isAllowed(@Valid @RequestBody RateLimitRequest request) {
        RateLimitResponse response = tokenBucketService.checkRateLimit(request.getClientKey(), request.getIdentifier());
        if (response.getDecision() == com.example.rate_limit.model.RateLimitDecision.DENY) {
            throw new com.example.rate_limit.exception.RateLimitExceededException(response);
        }
        return response;
    }
```

---

## Verification Plan

### Manual Verification
1. Start the local Redis container (if not running).
2. Start the Spring Boot application.
3. Test using `curl`:
   ```bash
   curl -i -X POST http://localhost:8080/api/v1/rate-limit/check \
     -H "Content-Type: application/json" \
     -d '{"clientKey":"default","identifier":"test-user"}'
   ```
4. Observe headers:
   - For `ALLOW` (200 OK): Verify `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` are present.
   - For `DENY` (429 Too Many Requests): Verify `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`, and `Retry-After` are present.
