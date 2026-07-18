# Rate Limiting Algorithms

The Rate Limiting Service supports multiple rate-limiting algorithms to cater to different use cases and traffic patterns. The core logic for these algorithms is written in **Lua scripts** and executed atomically within Redis.

---

## 1. Token Bucket

The Token Bucket algorithm provides a smooth rate of requests while allowing for short bursts of traffic.

### How it works
- **Bucket Capacity (`maxTokens`):** The maximum number of tokens the bucket can hold.
- **Refill Rate (`refillRate`):** The number of tokens added to the bucket per second.
- When a request arrives, the algorithm checks if there is at least 1 token in the bucket.
- If so, the token is consumed, and the request is **ALLOWED**.
- If the bucket is empty, the request is **DENIED**.
- The bucket refills continuously over time based on the `refillRate`, up to the `maxTokens` capacity.

### Ideal Use Cases
- Standard API rate limiting where occasional bursts are acceptable.
- Protecting backend services that can handle sudden spikes in traffic but need an overall cap on sustained load.

### Redis Implementation (`token_bucket.lua`)
The Lua script calculates the elapsed time since the last request and mathematically refills the tokens before evaluating the current request. It sets a TTL (Time-To-Live) on the bucket key to prevent memory leaks for inactive identifiers.

---

## 2. Sliding Window

The Sliding Window algorithm offers a highly precise rate limit by tracking the exact timestamps of individual requests. It prevents the "boundary effect" often seen in fixed-window algorithms (where a user can double their limit by sending requests at the end of one window and the start of the next).

### How it works
- **Window Size (`window`):** The duration (in milliseconds) of the sliding window.
- **Max Requests (`maxTokens`):** The maximum number of requests allowed within the window.
- Every request timestamp is recorded.
- Before evaluating a new request, the algorithm discards all request timestamps older than `current_time - window`.
- If the number of remaining requests is less than `maxTokens`, the new request is recorded and **ALLOWED**.
- Otherwise, it is **DENIED**.

### Ideal Use Cases
- Strict rate limiting where enforcing absolute limits over rolling time periods is critical (e.g., payment gateways, sensitive data endpoints).
- Mitigating abuse by preventing boundary spikes.

### Redis Implementation (`sliding_window.lua`)
The script uses Redis Sorted Sets (`ZSET`). The request timestamp serves as the score. The script uses `ZREMRANGEBYSCORE` to evict old requests, counts the remaining items with `ZCARD`, and inserts new requests with `ZADD`. It applies a `PEXPIRE` to the set based on the window duration to clean up stale data automatically.
