# Performance Report: Token Bucket vs. Sliding Window Rate Limiting

This document presents a side-by-side performance comparison of the **Token Bucket** and **Sliding Window** rate-limiting algorithms under identical heavy stress testing conditions.

---

## Executive Summary

Both rate-limiting implementations are extremely fast and efficient, showcasing sub-5ms median latencies under a peak load of **1000+ requests per second**. However, the **Sliding Window** algorithm demonstrated superior tail latencies at high percentiles (p99 and maximum response times).

* **Tail Latency Reduction**: The maximum response time for Sliding Window was **21 ms**, compared to **102 ms** for Token Bucket (a **79.4% reduction**).
* **Consistent Response Times**: The standard deviation for Sliding Window was only **1 ms** (compared to **3 ms** for Token Bucket), indicating extremely predictable execution times.
* **100% Success Rate**: Both algorithms processed all **21,052 requests** with **zero TCP connection drops or errors**, thanks to Gatling TCP connection reuse (`shareConnections`).

---

## Side-by-Side Comparison

### 1. Global Performance Metrics (21,052 Requests)

| Metric | Token Bucket | Sliding Window | Improvement |
| :--- | :--- | :--- | :--- |
| **Successful Requests (OK)** | 21,052 (100%) | 21,052 (100%) | - |
| **Failed Requests (KO)** | 0 (0%) | 0 (0%) | - |
| **Min Response Time** | 1 ms | 1 ms | - |
| **Mean Response Time** | 4 ms | 4 ms | - |
| **Median Response Time (p50)** | 3 ms | 3 ms | - |
| **75th Percentile (p75)** | 4 ms | 4 ms | - |
| **95th Percentile (p95)** | 6 ms | 6 ms | - |
| **99th Percentile (p99)** | **9 ms** | **7 ms** | **+22.2% (Faster)** |
| **Max Response Time** | **102 ms** | **21 ms** | **+79.4% (Faster)** |
| **Standard Deviation** | 3 ms | 1 ms | **+66.6% (More Stable)** |
| **Mean Throughput** | 601.49 req/sec | 601.49 req/sec | - |

---

## Detailed Scenario Breakdown

The stress tests consisted of three sequential phases designed to challenge the rate-limiters:
1. **Single Client Burst** (Ramp 1 → 200 RPS over 5s, sustain 200 RPS for 15s)
2. **Multi-Client Distributed Load** (Ramp 10 → 500 RPS over 10s, sustain 500 RPS for 20s)
3. **Spike Burst** (Sudden 1000 RPS for 5s)

### Response Time Distribution (Both Algorithms)

| Response Time Bucket | Token Bucket | Sliding Window |
| :--- | :--- | :--- |
| **< 800 ms** (Optimal) | 21,052 (100%) | 21,052 (100%) |
| **800 ms - 1200 ms** (Acceptable) | 0 (0%) | 0 (0%) |
| **≥ 1200 ms** (Degraded) | 0 (0%) | 0 (0%) |

---

## Architectural Performance Analysis

### Why Sliding Window Outperformed Token Bucket at Tail Latency:

1. **Simpler Math Operations in Lua**:
   - **Token Bucket** requires fetching the state from a Redis hash, calculating elapsed time, performing floating-point math to compute refilled tokens, and setting the new state. Floating-point division and multiplication in Lua add slight CPU overhead.
   - **Sliding Window** utilizes Redis's native sorted set commands (`ZREMRANGEBYSCORE`, `ZCARD`, `ZRANGE`, `ZADD`). Because our client ZSETs are small (capped at 100 elements under the test limit), these sorted set operations run entirely in-memory using highly optimized C code inside Redis. No custom floating-point math is computed.

2. **Predictable Memory and CPU Footprint**:
   - The standard deviation of **1 ms** in Sliding Window highlights its extreme predictability. The execution time of sorted set operations with small sizes is highly deterministic.

### Conclusion & Recommendation

* **For Low Latency/Tail-Sensitive Systems**: **Sliding Window** is highly recommended. It provides tighter latencies at high percentiles (p99) and is very predictable.
* **For High-Burst / Memory-Constrained Systems**: **Token Bucket** is recommended if memory consumption is a bottleneck, as it stores state in a simple hash key containing just two fields. Sliding Window requires storing every accepted request timestamp in a ZSET, which consumes more memory over time if there are millions of active clients and huge rate-limiting windows.

---

## How to Run These Tests Locally

To run these tests again and see the interactive HTML reports:

1. Start the Spring Boot Application:
   ```bash
   ./mvnw spring-boot:run
   ```
2. Run either test from another terminal:
   ```bash
   # Run Token Bucket
   ./mvnw gatling:test -Dgatling.simulationClass=com.example.rate_limit.TokenBucketSimulation

   # Run Sliding Window
   ./mvnw gatling:test -Dgatling.simulationClass=com.example.rate_limit.SlidingWindowSimulation
   ```
3. Open the generated HTML reports under `target/gatling/`.
