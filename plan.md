# Token Bucket Rate Limiter Service — Project Plan

> A standalone API that rate-limits other APIs — not a feature, a product.

---

## 1. What Is This?

A standalone Rate Limiter microservice that other projects/services call into to enforce rate limits on their own APIs. Given a **client key**, the service returns **ALLOW** or **DENY** based on a configurable algorithm (Token Bucket or Sliding Window).

### How It Works

```
┌──────────────┐         ┌──────────────────────┐         ┌─────────┐
│ Project Owner │──admin──▶│  Rate Limiter Service │◀──state──▶│  Redis  │
└──────────────┘         └──────────┬───────────┘         └─────────┘
                                    │
                          check ALLOW/DENY
                                    │
┌──────────┐    request    ┌────────▼────────┐
│ End User │──────────────▶│ Client's App    │
│          │◀──────────────│ (Payment API,   │
└──────────┘   response    │  Chat Service)  │
                           └─────────────────┘
```

**Flow:**
1. A **project owner** registers their `clientKey` and configures rate limits via the Admin API
2. Their **application** calls our Check API on every incoming request, passing the `clientKey`
3. We return ALLOW or DENY + standard rate-limit headers
4. Their app acts on the decision (serve the request or return 429)

---

## 2. Core Requirements

| # | Requirement | Status |
|---|---|---|
| 01 | Expose an endpoint that returns ALLOW/DENY based on a **token bucket** algorithm | ⬜ TODO |
| 02 | Support **per-client configurable limits** (RPS, burst size) set via an admin endpoint | ⬜ TODO |
| 03 | State must **survive a service restart** — persist bucket state, don't keep it only in memory | ⬜ TODO |
| 04 | Handle concurrent requests for the **SAME client key** without letting tokens be double-spent (race condition safe) | ⬜ TODO |
| 05 | Provide a **sliding-window mode** in addition to token bucket, selectable per client | ⬜ TODO |
| 06 | Return **standard rate-limit headers** (limit, remaining, reset time) on every response | ⬜ TODO |
| 07 | Include a **load test** proving correctness under 500+ concurrent requests per second | ⬜ TODO |

### Stretch Goals

| Goal | Status |
|---|---|
| Distributed mode — multiple instances share state correctly | ⬜ TODO |
| Tiny dashboard showing live request/deny rates per client | ⬜ TODO |

---

## 3. Tech Stack

| Layer | Technology | Why |
|---|---|---|
| **Framework** | Spring Boot 4.1 | Already scaffolded; mature REST support |
| **Language** | Java 17 | Configured in `pom.xml` |
| **State Store** | **Redis 7+** | Sub-ms latency, atomic Lua scripts, TTL-based auto-expiry, survives restarts via RDB/AOF |
| **Redis Client** | Spring Data Redis (Lettuce) | Non-blocking, thread-safe, built-in connection pooling |
| **Serialization** | Jackson | Request/response DTOs (ships with Spring Boot) |
| **Boilerplate** | Lombok | Already in `pom.xml` |
| **Validation** | Bean Validation (`spring-boot-starter-validation`) | DTO input validation |
| **Testing** | JUnit 5 + MockMvc + Testcontainers | Integration tests with real Redis in Docker |
| **Load Testing** | Gatling | Prove correctness under 500+ concurrent RPS |
| **Build** | Maven | Already scaffolded |

### Why Redis?

Rate-limit state is **ephemeral, high-frequency, and latency-sensitive**:
- A relational DB adds 1–5 ms per call and creates write-contention bottlenecks under 500+ RPS
- Redis gives **<0.5 ms** reads/writes
- **Atomic Lua scripts** eliminate race conditions without application-level locking (Req #04)
- **TTL** auto-cleans expired buckets — no cron jobs needed
- **Persistence** (RDB snapshots + AOF log) ensures state survives restarts (Req #03)

### Why Redis for Client Config Too?

- The Lua scripts need config (maxTokens, refillRate) at execution time — if it's in Redis, the script reads it atomically in the same call (zero extra round-trips)
- Keeps the stack to **one external dependency** (Redis only, no Postgres)
- Redis persistence is sufficient for config durability
- If we later need audit trails or complex querying, we can add a relational DB and treat Redis as a hot-store

---

## 4. Redis Schema

```
# Per-client configuration (Hash)
rate_limit:config:{clientKey}
  ├── maxTokens        (int)     — bucket capacity / burst size
  ├── refillRate       (double)  — tokens added per second
  ├── algorithm        (string)  — "TOKEN_BUCKET" | "SLIDING_WINDOW"
  └── createdAt        (long)    — epoch millis

# Per-client bucket state (Hash) — Token Bucket algorithm
rate_limit:bucket:{clientKey}
  ├── tokens           (double)  — current token count
  └── lastRefillTime   (long)    — epoch millis of last refill

# Per-client request log (Sorted Set) — Sliding Window algorithm
rate_limit:window:{clientKey}
  └── members: request timestamps (score = epoch millis)
```

**TTL Strategy:** Bucket and window keys are auto-expired after 2× the refill window to prevent unbounded memory growth from idle clients.

---

## 5. API Design

### 5.1 Rate Limit Check API

#### `POST /api/v1/rate-limit/check`

Check whether a request should be allowed or denied.

**Request Body:**
```json
{
  "clientKey": "payment-api"
}
```

**Response (ALLOWED) — 200 OK:**
```json
{
  "allowed": true,
  "limit": 100,
  "remaining": 87,
  "resetAtEpochMs": 1720000000000
}
```

**Response (DENIED) — 429 Too Many Requests:**
```json
{
  "allowed": false,
  "limit": 100,
  "remaining": 0,
  "retryAfterMs": 450,
  "resetAtEpochMs": 1720000000000
}
```

**Response Headers (on EVERY response):**
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 87
X-RateLimit-Reset: 1720000000
Retry-After: 1            (only on 429)
```

#### `GET /api/v1/rate-limit/check/{clientKey}`

Lightweight GET variant of the above.

---

### 5.2 Admin API

#### `POST /api/v1/admin/clients` — Create/Update Client Config

**Request Body:**
```json
{
  "clientKey": "payment-api",
  "maxTokens": 100,
  "refillRate": 10.0,
  "algorithm": "TOKEN_BUCKET"
}
```

**Response — 201 Created:**
```json
{
  "clientKey": "payment-api",
  "maxTokens": 100,
  "refillRate": 10.0,
  "algorithm": "TOKEN_BUCKET",
  "createdAt": 1720000000000
}
```

#### `GET /api/v1/admin/clients/{clientKey}` — Get Client Config

#### `GET /api/v1/admin/clients` — List All Client Configs

#### `DELETE /api/v1/admin/clients/{clientKey}` — Delete Client Config

---

## 6. Core Algorithms

### 6.1 Token Bucket (Default)

The token bucket algorithm works like a bucket that fills up with tokens at a steady rate:

```
Bucket Capacity = maxTokens (burst size)
Refill Rate     = refillRate tokens/second

On each request:
  1. Calculate elapsed time since last refill
  2. Add tokens: currentTokens + (elapsed × refillRate)
  3. Cap at maxTokens
  4. If tokens >= 1 → decrement by 1, ALLOW
  5. Else → DENY, return time until next token
```

**Implementation:** A single Lua script (`token_bucket.lua`) runs atomically inside Redis.
This means two concurrent requests for the same clientKey **cannot** both read the same token count and both decrement — the second one sees the decremented value. This solves Req #04.

### 6.2 Sliding Window Log (Alternative)

Instead of a bucket, track a log of all request timestamps in a sliding time window:

```
Window Size = 1 second (or derived from config)
Max Requests = maxTokens

On each request:
  1. Remove all entries older than (now - windowSize)
  2. Count remaining entries
  3. If count < maxRequests → add current timestamp, ALLOW
  4. Else → DENY, return when oldest entry will expire
```

**Implementation:** A single Lua script (`sliding_window.lua`) using a Redis Sorted Set (ZSET).

### Why Lua Scripts?

```
Without Lua (RACE CONDITION):
  Thread A: GET tokens → 1          Thread B: GET tokens → 1
  Thread A: SET tokens → 0, ALLOW   Thread B: SET tokens → 0, ALLOW
  ❌ Both allowed, but only 1 token existed!

With Lua (ATOMIC):
  Thread A: [GET + DECREMENT + RETURN] → tokens=0, ALLOW
  Thread B: [GET + DECREMENT + RETURN] → tokens=0, DENY ✅
```

---

## 7. Project Structure

```
com.example.rate_limit/
├── RateLimitApplication.java           — Entry point
│
├── config/
│   ├── RedisConfig.java                — RedisTemplate, Lua script beans
│   └── RateLimitProperties.java        — Default config via @ConfigurationProperties
│
├── controller/
│   ├── RateLimitController.java        — POST/GET /api/v1/rate-limit/check
│   └── AdminController.java           — CRUD /api/v1/admin/clients
│
├── dto/
│   ├── RateLimitRequest.java           — { clientKey }
│   ├── RateLimitResponse.java          — { allowed, limit, remaining, ... }
│   ├── ClientConfigRequest.java        — { clientKey, maxTokens, refillRate, algorithm }
│   └── ClientConfigResponse.java       — Admin API response
│
├── model/
│   ├── RateLimitAlgorithm.java         — Enum: TOKEN_BUCKET, SLIDING_WINDOW
│   └── ClientConfig.java              — Domain model
│
├── service/
│   ├── RateLimitService.java           — Orchestrator: fetch config → delegate to algorithm
│   ├── TokenBucketService.java         — Executes token_bucket.lua
│   ├── SlidingWindowService.java       — Executes sliding_window.lua
│   └── ClientConfigService.java        — CRUD for client configs in Redis
│
├── exception/
│   ├── GlobalExceptionHandler.java     — @ControllerAdvice
│   ├── RateLimitExceededException.java
│   └── ClientNotFoundException.java
│
└── interceptor/
    └── RateLimitHeaderInterceptor.java — Sets X-RateLimit-* headers on every response

src/main/resources/
├── application.properties
└── scripts/
    ├── token_bucket.lua
    └── sliding_window.lua
```

---

## 8. Implementation Phases

### Phase 1: Foundation
- [ ] Add Maven dependencies (spring-data-redis, validation, testcontainers, gatling)
- [ ] Configure Redis connection in `application.properties`
- [ ] Create `RedisConfig.java` (RedisTemplate + Lua script beans)
- [ ] Create `RateLimitProperties.java` (default config values)
- [ ] Create DTOs (`RateLimitRequest`, `RateLimitResponse`, `ClientConfigRequest`, `ClientConfigResponse`)
- [ ] Create model classes (`RateLimitAlgorithm`, `ClientConfig`)

### Phase 2: Core Algorithm (Token Bucket)
- [ ] Write `token_bucket.lua` script
- [ ] Create `TokenBucketService.java` — execute Lua script via RedisTemplate
- [ ] Create `ClientConfigService.java` — CRUD for client configs
- [ ] Create `RateLimitService.java` — orchestrator

### Phase 3: API Layer
- [ ] Create `RateLimitController.java` (POST + GET check endpoints)
- [ ] Create `AdminController.java` (CRUD for client configs)
- [ ] Create `RateLimitHeaderInterceptor.java` (X-RateLimit-* headers)
- [ ] Create `GlobalExceptionHandler.java` + custom exceptions
- [ ] Remove `HelloWorldController.java`

### Phase 4: Sliding Window
- [ ] Write `sliding_window.lua` script
- [ ] Create `SlidingWindowService.java`
- [ ] Wire algorithm selection into `RateLimitService` based on client config

### Phase 5: Testing & Verification
- [ ] Unit tests for service layer (mocked Redis)
- [ ] Integration tests with Testcontainers (real Redis)
- [ ] Gatling load test simulation (500+ concurrent RPS)
- [ ] Manual verification (restart survival, header correctness)

---

## 9. Requirements → Code Mapping

| Req | Requirement | Solved By |
|---|---|---|
| #01 | ALLOW/DENY endpoint (token bucket) | `RateLimitController` + `TokenBucketService` + `token_bucket.lua` |
| #02 | Per-client configurable limits via admin endpoint | `AdminController` + `ClientConfigService` + Redis Hash |
| #03 | State survives restart | Redis RDB/AOF persistence — state lives outside the JVM |
| #04 | Race-condition safe concurrency | Lua scripts execute atomically in Redis — no double-spend |
| #05 | Sliding window mode, selectable per client | `SlidingWindowService` + `sliding_window.lua` + `algorithm` field in config |
| #06 | Standard rate-limit headers on every response | `RateLimitHeaderInterceptor` + response DTOs |
| #07 | Load test proving 500+ concurrent RPS | Gatling simulation class |

---

## 10. Local Development Setup

### Prerequisites
- Java 17+
- Maven (or use `./mvnw` wrapper)
- Docker (for Redis)

### Start Redis
```bash
docker run -d --name redis-rate-limiter -p 6379:6379 redis:7-alpine
```

### Start the Application
```bash
./mvnw spring-boot:run
```

### Quick Smoke Test
```bash
# 1. Create a client config
curl -X POST http://localhost:8080/api/v1/admin/clients \
  -H "Content-Type: application/json" \
  -d '{"clientKey":"test-app","maxTokens":5,"refillRate":1.0,"algorithm":"TOKEN_BUCKET"}'

# 2. Check rate limit (should be ALLOWED)
curl -v http://localhost:8080/api/v1/rate-limit/check/test-app

# 3. Spam it 6 times (6th should be DENIED with 429)
for i in {1..6}; do
  echo "Request $i:"
  curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/rate-limit/check/test-app
  echo ""
done
```

---

## 11. Skills You'll Master

- Concurrency control (atomic operations, race condition prevention)
- Algorithm design (token bucket, sliding window)
- API contracts (standard rate-limit headers)
- Load testing (proving correctness under pressure)
- Redis Lua scripting
- Spring Boot service architecture
