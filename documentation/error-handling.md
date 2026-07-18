# Error Handling

The Rate Limiting Service returns standard HTTP status codes to indicate success or failure.

## HTTP 429 Too Many Requests

This is the standard response when an identifier exceeds their allocated rate limit.

**Example Response:**
```json
{
  "allowed": false,
  "decision": "DENY",
  "remaining": 0,
  "retryAfterMs": 1500,
  "maxTokens": 50,
  "resetAtEpochMs": 1721340001500
}
```
**Handling in Client:** 
Your application should parse the `retryAfterMs` value and use it to populate the standard `Retry-After` HTTP header before returning a 429 to the end user.

---

## HTTP 400 Bad Request

Returned when the payload sent to the API is invalid or missing required fields.

**Common Causes:**
- Missing `clientKey` or `identifier` in the `/check` request.
- Invalid data types (e.g., passing a string for `maxTokens` during client registration).
- For Admin API updates, providing a `clientKey` in the request body that does not match the `clientId` query parameter.

---

## HTTP 404 Not Found

Returned by the Admin API if you attempt to update, delete, or fetch details for a `clientKey` that does not exist in Redis.

**Example Cause:**
```bash
GET /api/v1/admin/client/details?clientId=non_existent_key
```

---

## HTTP 500 Internal Server Error

Returned if the application encounters an unexpected issue, typically related to infrastructure availability.

**Common Causes:**
- **Redis Connectivity Issues:** If Redis goes down, the Lua scripts cannot execute, and the rate limiter will fail. Check your `REDIS_URL` and ensure your Redis instance is online.
- **Thread Pool Saturation:** Under extreme load spikes (as noted in the load testing results), the embedded Tomcat server may refuse connections. This results in a connection timeout/refusal for the calling application. Horizontal scaling is required to mitigate this.
