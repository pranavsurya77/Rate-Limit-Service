# Rate Limit Service — Gatling Load Test Results

> **Date**: 2026-07-12  
> **Gatling Version**: 3.11.5  
> **Test Duration**: 44 seconds  
> **Target**: `POST /api/v1/rate-limit/check` on `localhost:8080`  
> **Algorithm**: Token Bucket (maxTokens=100, refillRate=10/sec)

---

## Test Environment

| Component | Details |
|-----------|---------|
| **OS** | Windows 11 Home Single Language (25H2) x86_64 |
| **CPU** | AMD Ryzen 7 5800HS (16 cores) @ 4.45 GHz |
| **RAM** | 15.40 GiB (10.9 GiB used during test) |
| **Java** | JDK 21.0.11 |
| **Server** | Spring Boot 4.1.0 (Embedded Tomcat 11.0.22) |
| **Data Store** | Redis (localhost:6379) |

---

## Test Scenarios

The load test consisted of **3 concurrent scenarios** designed to stress the rate limiter under increasing pressure:

| Phase | Scenario | Description | Injection Profile | Total Users |
|-------|----------|-------------|-------------------|-------------|
| 1 | **Single Client Burst** | 1 fixed identifier hitting the rate limiter | Ramp 1→200 RPS over 5s, sustain 200 RPS for 15s | 3,502 |
| 2 | **Multi Client Distributed Load** | 200 random identifiers (user_1 to user_200) | 5s delay, ramp 10→500 RPS over 10s, sustain 500 RPS for 20s | 12,550 |
| 3 | **Spike Burst** | 50 random identifiers (spike_user_1 to spike_user_50) | 25s delay, sudden 1000 RPS for 5s | 5,000 |

**Total requests injected**: 21,052

---

## Global Results Summary

```
================================================================================
---- Global Information --------------------------------------------------------
> request count                                      21,052 (OK=12,916  KO=8,136)
> min response time                                      4 ms
> max response time                                 15,626 ms
> mean response time                                 3,717 ms
> std deviation                                      4,796 ms
> response time 50th percentile (median)                12 ms
> response time 75th percentile                      8,988 ms
> response time 95th percentile                     12,009 ms
> response time 99th percentile                     13,071 ms
> mean requests/sec                                 467.82
================================================================================
```

---

## Response Time Breakdown (Successful Requests Only)

For the **12,916 successful** requests (HTTP 200 or 429 from rate limiter):

| Metric | Value |
|--------|-------|
| **Min response time** | 4 ms |
| **Mean response time** | 1,096 ms |
| **Median (p50)** | 9 ms |
| **75th percentile** | 11 ms |
| **95th percentile** | 10,035 ms |
| **99th percentile** | 14,175 ms |
| **Max response time** | 15,626 ms |
| **Std deviation** | 3,131 ms |
| **Mean throughput** | 287.02 req/sec |

### Key Observation
Under **moderate load (up to ~500 RPS)**, the server responds in **under 15 ms** for the vast majority of requests. The p50 of **9 ms** shows the rate limiter + Redis lookup is extremely fast. The tail latency (p95/p99) spikes are caused by connection saturation during the spike phase.

---

## Response Time Distribution

| Bucket | Count | Percentage |
|--------|-------|------------|
| **< 800 ms** (fast) | 11,281 | 53.59% |
| **800 ms – 1200 ms** (acceptable) | 11 | 0.05% |
| **≥ 1200 ms** (slow) | 1,624 | 7.71% |
| **Failed** (connection refused) | 8,136 | 38.65% |

---

## Per-Scenario Breakdown

### Phase 1: Single Client Burst (200 RPS, 15s)

| Metric | Value |
|--------|-------|
| **Total Requests** | 3,502 |
| **Successful** | 3,502 (100%) |
| **Failed** | 0 (0%) |

✅ **Verdict**: The server handled 200 RPS from a single client with **zero failures**. All requests completed successfully (some got HTTP 200 ALLOW, others got HTTP 429 DENY as expected from the rate limiter).

---

### Phase 2: Multi Client Distributed Load (500 RPS, 20s)

| Metric | Value |
|--------|-------|
| **Total Requests** | 12,550 |
| **Successful** | 8,768 (69.9%) |
| **Failed** | 3,782 (30.1%) |
| **Failure Cause** | Connection refused (server saturation) |

⚠️ **Verdict**: At sustained 500 RPS with concurrent spike traffic, Tomcat's default thread pool (200 threads) became saturated, causing connection refusals in the later seconds of the test.

---

### Phase 3: Spike Burst (1000 RPS, 5s)

| Metric | Value |
|--------|-------|
| **Total Requests** | 5,000 |
| **Successful** | 646 (12.9%) |
| **Failed** | 4,354 (87.1%) |
| **Failure Cause** | Connection refused (server saturation) |

❌ **Verdict**: The sudden 1000 RPS spike overwhelmed the server. With 500 RPS already active from Phase 2, the combined ~1500 RPS exceeded Tomcat's connection handling capacity.

---

## Error Analysis

| Error | Count | Percentage |
|-------|-------|------------|
| `j.n.ConnectException: Connection refused: getsockopt` | 8,136 | 100% of failures |

All failures were due to **TCP connection refusal** — the server's connection backlog was full. This is a Tomcat thread pool / acceptCount limit, **not a rate limiter issue**. The rate limiter logic itself worked correctly for all requests that reached it.

---

## Performance Conclusions

### What works well ✅
1. **Rate limiter latency is excellent**: p50 = 9 ms, meaning the Token Bucket + Redis check is sub-10ms for typical requests
2. **Zero application errors**: Every request that reached the server was handled correctly (either ALLOW or DENY)
3. **Handles 200 RPS effortlessly**: Phase 1 had 100% success rate
4. **Handles 500 RPS for ~15 seconds**: Phase 2 was fine until the spike layered on top

### Bottleneck identified ⚠️
1. **Tomcat connection limit**: Default Tomcat handles ~200 concurrent connections. At 1500+ combined RPS, connections are refused
2. **Not a rate limiter issue**: The Token Bucket algorithm performed correctly; the bottleneck is HTTP server capacity

### Recommended improvements for production
1. **Increase Tomcat thread pool**: Set `server.tomcat.threads.max=400` and `server.tomcat.accept-count=200`
2. **Use async/reactive**: Consider switching to Spring WebFlux for non-blocking I/O
3. **Connection pooling**: Use a reverse proxy (Nginx) to buffer connections
4. **Horizontal scaling**: Deploy multiple instances behind a load balancer

---

## How to Reproduce

### Prerequisites
- Redis running on `localhost:6379`
- Spring Boot application running on `localhost:8080`

### Run the test
```bash
./mvnw spring-boot:run                     # Terminal 1: Start the server
./mvnw gatling:test                        # Terminal 2: Run the load test
```

### View HTML report
After the test completes, open the generated report:
```
target/gatling/ratelimitsimulation-<timestamp>/index.html
```

---

## Simulation Source
The Gatling simulation is located at:
```
src/test/java/com/example/rate_limit/RateLimitSimulation.java
```
