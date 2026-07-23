# Rate Limiting Service Documentation

Welcome to the official documentation for the **Production-Grade Rate Limiting Service** — a high-throughput, low-latency rate-limiting solution built with **Spring Boot 4.1** and **Redis**.

***

## Overview

The Rate Limiting Service acts as a centralized, API-driven guardrail for your microservices, public APIs, and payment gateways. By offloading rate limit evaluations to atomic Redis Lua scripts paired with local 30-second configuration caching, the service delivers sub-10ms response times without race conditions or distributed lock overhead.

![Payment API Rate Limiting Sequence Diagram](.gitbook/assets/payment_api_sequence_diagram.png)

***

## Core Capabilities

| Feature                     | Description                                                                                                                            |
| --------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| ⚡ **Sub-10ms Latency**      | Evaluates requests with a median (p50) response time of **9 ms** under load.                                                           |
| 🛡️ **Atomic Evaluation**   | Multi-step rate checks execute inside Redis Lua scripts, eliminating race conditions across distributed nodes.                         |
| 🔄 **Dual Algorithms**      | Native support for **Token Bucket** (burst traffic) and **Sliding Window** (precision rate caps).                                      |
| 🚀 **Local Config Caching** | Spring Boot instances cache client configurations locally in memory with event-driven cache invalidation (`ClientConfigChangedEvent`). |
| 🌐 **Distributed Ready**    | Fully stateless application instances scaling horizontally behind a load balancer.                                                     |

***

## Explore the Documentation

> 📖 **Full documentation is also available on [GitBook](https://mptube.gitbook.io/mptube-docs).**

Navigate through the key sections to get started:

* 🚀 [**Quick Start & Integration**](documentation/quick-start.md) — Step-by-step installation, running locally, and middleware integration examples.
* 🏗️ [**System Architecture**](documentation/architecture.md) — In-depth breakdown of Spring Boot, local caching, Redis Lua scripts, and request execution flows.
* ⚙️ [**Rate Limiting Algorithms**](documentation/algorithms.md) — Mathematical and implementation details for Token Bucket and Sliding Window algorithms.
* 🔑 [**Understanding Identifiers**](documentation/identifiers.md) — How to isolate limits using User IDs, API Keys, IP Addresses, and multi-tenant keys.
* 📡 [**API Reference**](documentation/api-reference.md) — Complete OpenAPI specification for `/check` and `/admin/client/*` endpoints.
* 🛠️ [**Configuration Reference**](documentation/configuration.md) — Environment variables (`REDIS_URL`, `PORT`) and Client entity parameter schemas.
* ⚠️ [**Error Handling**](documentation/error-handling.md) — Standard HTTP 429 response handling and `Retry-After` header calculations.
* 📊 [**Performance Optimization Journey**](documentation/performance.md) — Engineering case study, Gatling benchmark analysis, and production tuning recommendations.
