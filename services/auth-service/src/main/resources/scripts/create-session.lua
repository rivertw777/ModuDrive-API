-- KEYS[1] = session:{sha256(id)}, KEYS[2] = member-session:{memberId}
-- ARGV[1] = memberId, ARGV[2] = roles (comma-separated), ARGV[3] = idle timeout in ms,
-- ARGV[4] = absolute timeout in ms, ARGV[5] = sha256(id) (the value KEYS[1] ends with)
-- createdAt comes from the Redis clock so every auth-service instance measures the absolute
-- timeout against the same time source as touch-session.lua.
local t = redis.call('TIME')
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)

-- One session per member (spec 004 1-2): the one the member held is marked replaced rather than
-- deleted, so its browser can be told why, and keeps its own TTL. Skipped when it's already gone —
-- HSET on a missing key would create one with no TTL.
-- ponytail: the old key is built here, not passed in KEYS, so this can't run on Redis Cluster;
-- ElastiCache must stay cluster mode disabled.
local old = redis.call('GET', KEYS[2])
if old and redis.call('EXISTS', 'session:' .. old) == 1 then
  redis.call('HSET', 'session:' .. old, 'replaced', '1')
  redis.call('HDEL', 'session:' .. old, 'memberId', 'roles')
end

redis.call('HSET', KEYS[1], 'memberId', ARGV[1], 'roles', ARGV[2], 'createdAt', now)
redis.call('PEXPIRE', KEYS[1], ARGV[3])
redis.call('SET', KEYS[2], ARGV[5], 'PX', ARGV[4])
return 1
