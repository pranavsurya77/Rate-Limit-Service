
## Latency Breakdown & Optimization Strategies

Your current p50 is **9ms** — that's already decent, but hitting a consistent **<5ms** is achievable. Here's where the time goes and what you can do about each layer:

### 🔍 Where the 9ms is being spent

| Layer | Estimated Cost | What's Happening |
|-------|---------------|-------------------|
| **Tomcat thread dispatch** | ~0.5ms | Request parsing, thread pool checkout |
| **Jackson deserialization** | ~1-2ms | `@RequestBody` JSON → `RateLimitRequest` via `GenericJacksonJsonRedisSerializer` |
| **Redis roundtrip** | ~3-5ms | Lua script execution + network hop to Redis |
| **Redis serialization** | ~1ms | `GenericJacksonJsonRedisSerializer` adds type metadata overhead |
| **Jackson serialization (response)** | ~1ms | `RateLimitResponse` → JSON |
| **ResponseBodyAdvice** | ~0.2ms | Header injection |

### 🚀 Concrete Improvements (Ordered by Impact)

**1. Switch to Lettuce connection pooling (biggest win: -1-3ms)**

Your current setup uses Spring's default Lettuce single-connection. Under concurrent load, requests queue for the same connection. Add to `application.properties`:

```properties
spring.data.redis.lettuce.pool.enabled=true
spring.data.redis.lettuce.pool.max-active=16
spring.data.redis.lettuce.pool.max-idle=8
spring.data.redis.lettuce.pool.min-idle=4
```

And add the `commons-pool2` dependency to `pom.xml`:
```xml
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
</dependency>
```

**2. Replace `GenericJacksonJsonRedisSerializer` with `StringRedisSerializer` (win: -0.5-1ms)**

In [RedisConfig.java](file:///c:/Users/pranav/wd/project/rate/rate_limit/src/main/java/com/example/rate_limit/config/RedisConfig.java), `GenericJacksonJsonRedisSerializer` embeds `@class` type info in every value — unnecessary for Lua scripts that deal with raw numbers. Using `StringRedisSerializer` for values too would eliminate serialization/deserialization overhead.

**3. Optimize the Lua script (win: -0.5-1ms)**

In [token_bucket.lua](file:///c:/Users/pranav/wd/project/rate/rate_limit/src/main/resources/scripts/token_bucket.lua#L13-L18), you're making **3 separate Redis commands** inside the script:
- `EXISTS` + `HGET` + `HGET` for config (lines 13-17)
- `HMGET` for bucket state (line 24)

You can collapse the config check into a single `HMGET`:
```lua
local config = redis.call('HMGET', config_key, 'maxTokens', 'refillRate')
if config[1] then max_tokens = tonumber(config[1]) end
if config[2] then refill_rate = tonumber(config[2]) end
```
This eliminates the `EXISTS` call — `HMGET` on a non-existent key just returns `nil` values.

**4. Switch to Spring WebFlux / Reactive (win: eliminates thread-pool bottleneck)**

This is the biggest architectural change. Instead of Tomcat's blocking thread model (200 threads → connection refused under load), WebFlux with Netty handles thousands of connections on a few event-loop threads. Your Redis calls would use `ReactiveRedisTemplate` which is non-blocking. This is what services like Cloudflare and AWS API Gateway use internally.

**5. Cache client config in-memory (win: -1-2ms)**

Right now, every single rate limit check reads client config from Redis (`HMGET` on `config_key`). Since client configs change rarely, you could:
- Cache them in a `ConcurrentHashMap` with a short TTL (e.g. 30s)
- Pass `max_tokens` and `refill_rate` directly to the Lua script from Java (you already have the defaults!)
- This eliminates one entire Redis command from the Lua script

**6. Avoid exception-driven flow for DENY responses (win: ~0.5ms)**

In [RateLimitController.java](file:///c:/Users/pranav/wd/project/rate/rate_limit/src/main/java/com/example/rate_limit/controller/RateLimitController.java#L25-L27), DENY responses throw a `RateLimitExceededException` which goes through the full exception handler stack. Exception creation (with stack trace) is expensive in Java. Instead, return the response directly with the appropriate status code using `ResponseEntity`.

### 📊 Expected Impact

| Optimization | Current p50 | Expected p50 |
|-------------|------------|-------------|
| Baseline | 9 ms | 9 ms |
| + Connection pooling | | ~5-6 ms |
| + StringRedisSerializer | | ~5 ms |
| + Lua script optimization | | ~4 ms |
| + Client config cache | | ~3 ms |
| + Remove exception flow | | ~2.5 ms |
| + WebFlux (optional) | | ~1-2 ms |

### ⚡ Quick Wins (can implement right now)

The easiest ones that don't require architectural changes are **1, 3, 5, and 6** — connection pooling, Lua optimization, config caching, and removing the exception flow. Want me to implement those? That should bring you to a consistent **3-5ms** range.