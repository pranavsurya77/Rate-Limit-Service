# Understanding Identifiers

In the Rate Limiting Service, an **identifier** represents the specific entity being rate-limited within the domain of a client application. 

Identifiers allow a single client configuration (e.g., "E-Commerce Gateway") to enforce rate limits on millions of distinct users, IPs, or devices independently.

## What is an Identifier?
An identifier is an arbitrary string passed by the client application in the `/check` payload. 

Examples of common identifiers include:
- **User ID:** `user_789456`
- **Account ID:** `acct_12345`
- **Customer ID:** `cust_abcd`
- **API Key:** `sk_live_xyz123`
- **Device ID:** `dev_987`
- **Session ID:** `sess_abc`
- **Tenant ID:** `org_555`
- **IP Address:** `192.168.1.1`

## How Identifiers are used Internally

When the service receives a request, it combines the `clientKey` and the `identifier` to construct a unique Redis key for storing the rate limit state. 

For example, if the `clientKey` is `payment_gateway` and the `identifier` is `user_123`, the Redis key used for the Token Bucket algorithm might look like:
`ratelimit:token_bucket:payment_gateway:user_123`

This guarantees that:
1. `user_123` on `payment_gateway` does not share a limit with `user_123` on `search_api`.
2. `user_123` does not share a limit with `user_456` on the same gateway.

## Best Practices
- **Consistency:** Always use a stable identifier format. Changing case or encoding (e.g., `user_123` vs `USER_123`) will result in separate rate limit buckets.
- **Granularity:** Choose the identifier based on the resource you want to protect. If you want to prevent global IP abuse, use the IP Address. If you want to limit API usage per paying customer, use their Account ID or API Key.
- **Length:** Keep identifiers reasonably short (e.g., UUIDs or database IDs) to conserve memory in Redis.

## Multi-Tenant Considerations
If your application serves multiple tenants, it is highly recommended to prefix the identifier with the tenant ID to prevent accidental collisions if two tenants use overlapping ID sequences.
- *Example:* `tenantA_user123`

## Example Integration
When your application calls the Rate Limiting Service, you provide the identifier dynamically:

```json
{
  "clientKey": "public_api",
  "identifier": "192.168.1.150"
}
```
If the limit is 100 requests per minute, that specific IP address can make 100 requests, independently of all other IP addresses.
