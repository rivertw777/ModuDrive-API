-- KEYS[1] = session:{sha256(id)}
-- ARGV[1] = memberId, ARGV[2] = roles (comma-separated), ARGV[3] = idle timeout in ms
-- createdAt comes from the Redis clock so every auth-service instance measures the absolute
-- timeout against the same time source as touch-session.lua.
local t = redis.call('TIME')
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
redis.call('HSET', KEYS[1], 'memberId', ARGV[1], 'roles', ARGV[2], 'createdAt', now)
redis.call('PEXPIRE', KEYS[1], ARGV[3])
return 1
