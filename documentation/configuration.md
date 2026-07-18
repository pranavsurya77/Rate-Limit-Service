# Configuration Reference

The Rate Limiting Service relies on two levels of configuration: 
1. **Server Configuration** (Environment variables for the application itself)
2. **Client Configuration** (Settings for the applications being rate-limited)

---

## 1. Server Configuration

These values configure the Spring Boot application and its connection to Redis. They can be set in an `.env` file or exported directly in your environment.

| Variable Name | Description | Default Value | Example |
|---------------|-------------|---------------|---------|
| `REDIS_URL` | The connection string for your Redis instance. | `localhost:6379` | `rediss://default:pwd@host:6379` |
| `PORT` | The HTTP port the service listens on. | `8080` | `8080` |

*Note: In production environments like Render or Heroku, the `PORT` variable is often injected automatically.*

---

## 2. Client Configuration (Admin API)

When registering a client via the Admin API (`POST /api/v1/admin/client/register`), you configure the rules for how that specific client's traffic will be evaluated.

### `clientKey` (Required)
- **Type:** String
- **Description:** A unique identifier for the client application.
- **Example:** `mobile_app_v1`

### `algorithm` (Required)
- **Type:** String (Enum)
- **Description:** The rate-limiting algorithm to apply.
- **Allowed Values:** `TOKEN_BUCKET`, `SLIDING_WINDOW`

### `maxTokens` (Required)
- **Type:** Integer
- **Description:** 
  - For Token Bucket: The maximum capacity of the bucket (burst limit).
  - For Sliding Window: The maximum number of requests allowed in the window.
- **Example:** `100`

### `refillRate` (Required for Token Bucket)
- **Type:** Integer
- **Description:** The number of tokens added back to the bucket per second. 
- **Example:** `10`
- *Note: Ignored if algorithm is SLIDING_WINDOW.*

### `window` (Required for Sliding Window)
- **Type:** Integer
- **Description:** The size of the sliding window in milliseconds.
- **Example:** `60000` (1 minute)
- *Note: Ignored if algorithm is TOKEN_BUCKET.*
