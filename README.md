# Rate Limit Service

A standalone, API-driven rate limiting service built with **Spring Boot 4.1** and **Redis**. Clients (developers/companies) register their rate limiting configuration, and then their backend services call this API to check whether an incoming user request should be **allowed** or **denied** based on the configured limits.

## Architecture Overview

![Rate Limit Service Flow](Payment%20API%20Rate%20Limiting-2026-07-11-054713.png)

## Tech Stack

| Technology | Purpose |
|---|---|
| Java 17 | Language |
| Spring Boot 4.1.0 | Web framework |
| Spring Data Redis | Client config persistence (`@RedisHash`) |
| Redis | Data store (client configs + rate limit state) |
| Lombok | Boilerplate reduction |
| Jakarta Validation | Request DTO validation |
| Maven | Build tool |

## Project Structure

```
src/main/java/com/example/rate_limit/
├── RateLimitApplication.java              # Spring Boot entry point
├── config/                                # Configuration classes (Redis, etc.)
├── controller/
│   ├── ClientController.java              # Client management endpoints
│   └── RateLimitController.java           # Rate limit check endpoint
├── dto/
│   ├── request/
│   │   ├── ClientConfigRequest.java       # Register/update a client
│   │   └── RateLimitRequest.java          # Check rate limit for a user
│   └── response/
│       ├── ClientConfigResponse.java      # Client config details
│       └── RateLimitResponse.java         # ALLOW/DENY decision
├── model/
│   ├── Client.java                        # Redis entity (@RedisHash)
│   ├── RateLimitAlgorithm.java            # Enum: TOKEN_BUCKET, SLIDING_WINDOW
│   └── RateLimitDecision.java             # Enum: ALLOW, DENY
├── repository/
│   └── ClientRepository.java             # Spring Data CrudRepository for Client
└── service/
    ├── ClientService.java                 # Service interface (declarations)
    └── impl/
        └── ClientServiceImplementation.java  # Service implementation (logic)
```

## Rate Limiting Algorithms

| Algorithm | Description |
|---|---|
| **Token Bucket** | Tokens are added at a fixed `refillRate`. Each request consumes a token. Requests are denied when the bucket is empty. |
| **Sliding Window** | Tracks request counts within a sliding time window. Requests are denied when the count exceeds `maxTokens`. |

## API Endpoints

### Client Management

| Method | Endpoint | Description | Request Body |
|---|---|---|---|
| `POST` | `/api/clients` | Register a new client | `ClientConfigRequest` |
| `GET` | `/api/clients/{clientKey}` | Get client configuration | — |

#### `ClientConfigRequest`

```json
{
  "clientKey": "my-app-key",
  "maxTokens": 100,
  "refillRate": 10,
  "algorithm": "TOKEN_BUCKET"
}
```

**Validation Rules:**
- `clientKey` — required, must not be blank
- `maxTokens` — required, minimum value of 1
- `refillRate` — required, minimum value of 1
- `algorithm` — required, must be `TOKEN_BUCKET` or `SLIDING_WINDOW`

#### `ClientConfigResponse`

```json
{
  "clientId": "abc123",
  "clientKey": "my-app-key",
  "maxTokens": 100,
  "refillRate": 10,
  "algorithm": "TOKEN_BUCKET",
  "createdAt": "2026-07-11T12:00:00Z",
  "updatedAt": "2026-07-11T12:00:00Z"
}
```

### Rate Limit Check

| Method | Endpoint | Description | Request Body |
|---|---|---|---|
| `POST` | `/api/rate-limit/check` | Check if a request is allowed | `RateLimitRequest` |

#### `RateLimitRequest`

```json
{
  "clientKey": "my-app-key",
  "identifier": "192.168.1.100"
}
```

**Validation Rules:**
- `clientKey` — required, must not be blank
- `identifier` — required, must not be blank (end-user's IP, user ID, API key, etc.)

#### `RateLimitResponse`

```json
{
  "decision": "ALLOW",
  "remaining": 99,
  "retryAfterMs": 0
}
```

```json
{
  "decision": "DENY",
  "remaining": 0,
  "retryAfterMs": 5000
}
```

## Data Model

### Client (Redis Hash)

Stored in Redis under the `clients` hash namespace.

| Field | Type | Description |
|---|---|---|
| `id` | `String` | Auto-generated Redis key |
| `clientKey` | `String` | Unique key identifying the client |
| `maxTokens` | `int` | Maximum number of tokens/requests allowed |
| `refillRate` | `int` | Rate at which tokens are refilled |
| `algorithm` | `RateLimitAlgorithm` | `TOKEN_BUCKET` or `SLIDING_WINDOW` |
| `createdAt` | `Instant` | Timestamp of creation |
| `updatedAt` | `Instant` | Timestamp of last update |

## Prerequisites

- **Java 17** or higher
- **Maven 3.8+**
- **Redis** running locally on port `6379`

## Getting Started

### 1. Clone the repository

```bash
git clone git@github.com:pranavsurya77/Rate-Limit-Service.git
cd Rate-Limit-Service
```

### 2. Start Redis

Make sure Redis is running locally:

```bash
# Using Docker
docker run -d --name redis -p 6379:6379 redis

# Or if installed locally
redis-server
```

### 3. Run the application

```bash
./mvnw spring-boot:run
```

The application starts on `http://localhost:8080` by default.

### 4. Test the API

```bash
# Register a client
curl -X POST http://localhost:8080/api/clients \
  -H "Content-Type: application/json" \
  -d '{"clientKey":"my-app","maxTokens":100,"refillRate":10,"algorithm":"TOKEN_BUCKET"}'

# Check rate limit
curl -X POST http://localhost:8080/api/rate-limit/check \
  -H "Content-Type: application/json" \
  -d '{"clientKey":"my-app","identifier":"user-123"}'
```

## Configuration

### `application.properties`

```properties
spring.application.name=rate_limit
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

### Environment-Specific Configuration

For production or environments with Redis authentication, use environment variables:

```properties
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}
spring.data.redis.password=${REDIS_PASSWORD:}
```

## Security Note

The `clientKey` is a **server-side secret** — it should only be used in backend-to-backend communication. End users should never have direct access to this key. Always use HTTPS in production.

## License

This project is open source.
