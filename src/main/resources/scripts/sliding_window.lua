local zset_key = KEYS[1]
local config_key = KEYS[2]

local now = tonumber(ARGV[1])
local default_max_requests = tonumber(ARGV[2])
local default_window = tonumber(ARGV[3])
local request_id = ARGV[4] or "" -- Unique request identifier (e.g. UUID) to prevent same-millisecond collision

-- fetch client configs
local max_tokens = default_max_requests
local window = default_window

-- if there is some custom value set for the client then get that
if config_key and config_key ~= "" then
    local config = redis.call('HMGET', config_key, 'maxTokens', 'window')
    if config[1] then max_tokens = tonumber(config[1]) end
    if config[2] then window = tonumber(config[2]) end
end

local current_time = now

-- remove all the request timestamps that are outside the window
redis.call('ZREMRANGEBYSCORE', zset_key, 0, current_time - window)

-- get the oldest element in the window to calculate retry/reset times
local oldest = redis.call('ZRANGE', zset_key, 0, 0, 'WITHSCORES')
local oldest_time = current_time
if oldest[1] then
    oldest_time = tonumber(oldest[2])
end

local elements = redis.call('ZCARD', zset_key)
local allowed = 0
local remaining = 0
local retry_after_ms = 0
local reset_at_epoch_ms = oldest_time + window

if elements < max_tokens then
    allowed = 1
    local member = current_time .. ":" .. request_id
    redis.call('ZADD', zset_key, current_time, member)
    remaining = max_tokens - elements - 1
else
    allowed = 0
    remaining = 0
    retry_after_ms = math.max(0, (oldest_time + window) - current_time)
end

-- Update TTL to keep the sliding window alive
redis.call('PEXPIRE', zset_key, window)

return { allowed, remaining, retry_after_ms, max_tokens, reset_at_epoch_ms }
