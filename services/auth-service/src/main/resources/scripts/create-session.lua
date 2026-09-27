-- KEYS[1] = session:{sha256(id)}, KEYS[2] = member-sessions:{memberId}
-- ARGV[1] = memberId, ARGV[2] = roles (comma-separated), ARGV[3] = idle timeout in ms,
-- ARGV[4] = absolute timeout in ms
-- createdAt comes from the Redis clock so every auth-service instance measures the absolute
-- timeout against the same time source as touch-session.lua.
local t = redis.call('TIME')
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
redis.call('HSET', KEYS[1], 'memberId', ARGV[1], 'roles', ARGV[2], 'createdAt', now)
redis.call('PEXPIRE', KEYS[1], ARGV[3])
-- The member's sessions, so a password change can end them all. No session outlives its absolute
-- timeout, so the index doesn't either past the newest one's.
redis.call('SADD', KEYS[2], KEYS[1])
redis.call('PEXPIRE', KEYS[2], ARGV[4])
return 1
