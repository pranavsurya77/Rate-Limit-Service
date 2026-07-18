# API Reference

The Rate Limiting Service provides two primary sets of endpoints:
1. **Rate Limit Evaluation:** Used by your applications to check if a request is allowed.
2. **Admin API:** Used to register, update, and manage Client configurations.

---

## 1. Rate Limit Evaluation

### Check Rate Limit
Evaluates a request against the configured rate limits.

**Endpoint:** `POST /api/v1/rate-limit/check`

**Request Body:**
```json
{
  "clientKey": "payment_gateway_prod",
  "identifier": "user_9012"
}
```
- `clientKey` (string, required): The ID of the configured client.
- `identifier` (string, required): The entity to rate limit (e.g., User ID, IP).

**Success Response (200 OK):**
Returned when the request is within the allowed limits.
```json
{
  "allowed": true,
  "decision": "ALLOW",
  "remaining": 49,
  "retryAfterMs": 0,
  "maxTokens": 50,
  "resetAtEpochMs": 1721340000000
}
```

**Rate Limit Exceeded Response (429 Too Many Requests):**
Returned when the rate limit has been exceeded.
```json
{
  "allowed": false,
  "decision": "DENY",
  "remaining": 0,
  "retryAfterMs": 2500,
  "maxTokens": 50,
  "resetAtEpochMs": 1721340002500
}
```

---

## 2. Admin API

### Register a Client
Creates a new rate-limiting configuration for a client application.

**Endpoint:** `POST /api/v1/admin/client/register`

**Request Body:**
```json
{
  "clientKey": "payment_gateway_prod",
  "maxTokens": 100,
  "refillRate": 10,
  "window": 60000,
  "algorithm": "TOKEN_BUCKET"
}
```
- `algorithm` can be `TOKEN_BUCKET` or `SLIDING_WINDOW`.

**Response (201 Created):**
```json
{
  "id": "5f4b...",
  "clientKey": "payment_gateway_prod",
  "maxTokens": 100,
  "refillRate": 10,
  "window": 60000,
  "algorithm": "TOKEN_BUCKET",
  "createdAt": "2026-07-18T10:00:00Z",
  "updatedAt": "2026-07-18T10:00:00Z"
}
```

### Update a Client
Updates an existing client configuration.

**Endpoint:** `PUT /api/v1/admin/client/update?clientId={clientKey}`

**Request Body:**
```json
{
  "clientKey": "payment_gateway_prod",
  "maxTokens": 200,
  "refillRate": 20,
  "algorithm": "TOKEN_BUCKET"
}
```
*Note: The `clientKey` in the request body must match the `clientId` query parameter.*

**Response (200 OK):**
Returns the updated client object.

### Get Client Details
Retrieves configuration for a specific client.

**Endpoint:** `GET /api/v1/admin/client/details?clientId={clientKey}`

**Response (200 OK):**
Returns the client object.

### Delete a Client
Removes a client configuration.

**Endpoint:** `DELETE /api/v1/admin/client/delete?clientId={clientKey}`

**Response (200 OK):**
`Client Deleted Successfully`
