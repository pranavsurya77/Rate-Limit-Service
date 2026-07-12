local bucket_key = KEYS[1]
local config_key = KEYS[2]

local now = tonumber(ARGV[1])
local default_max_tokens = tonumber(ARGV[2])
local default_refill_rate = tonumber(ARGV[3])
local consume = tonumber(ARGV[4]) or 1.0

-- 1. Fetch client config
local max_tokens = default_max_tokens
local refill_rate = default_refill_rate

if redis.call('EXISTS', config_key) == 1 then
    local config_max = redis.call('HGET', config_key, 'maxTokens')
    local config_rate = redis.call('HGET', config_key, 'refillRate')
    if config_max then max_tokens = tonumber(config_max) end
    if config_rate then refill_rate = tonumber(config_rate) end
end

-- 2. Fetch current bucket state
local tokens = max_tokens
local last_refill_time = now

local state = redis.call('HMGET', bucket_key, 'tokens', 'lastRefillTime')
if state[1] and state[2] then
    tokens = tonumber(state[1])
    last_refill_time = tonumber(state[2])
end

-- 3. Calculate elapsed time and refill tokens
local elapsed = math.max(0, now - last_refill_time) / 1000.0
local refilled_tokens = elapsed * refill_rate
local current_tokens = math.min(max_tokens, tokens + refilled_tokens)

-- 4. Check if request is allowed
local allowed = 0
local remaining = current_tokens
local retry_after_ms = 0

if current_tokens >= consume then
    allowed = 1
    current_tokens = current_tokens - consume
    remaining = current_tokens
    last_refill_time = now
    
    -- Save new state
    redis.call('HMSET', bucket_key, 'tokens', current_tokens, 'lastRefillTime', last_refill_time)
else
    -- Denied. Calculate time until at least 'consume' tokens are available.
    local needed_tokens = consume - current_tokens
    retry_after_ms = math.ceil((needed_tokens / refill_rate) * 1000.0)
end

-- 5. Set TTL on the bucket key to prevent memory leaks (e.g. max of 60s or 2x the time to refill full bucket)
local ttl = math.max(60, math.ceil((max_tokens / refill_rate) * 2))
redis.call('EXPIRE', bucket_key, ttl)

-- Calculate reset time (when bucket refills fully)
local reset_after_ms = math.ceil(((max_tokens - current_tokens) / refill_rate) * 1000)
local reset_at_epoch_ms = now + reset_after_ms

return { allowed, math.floor(remaining), retry_after_ms, max_tokens, reset_at_epoch_ms }
