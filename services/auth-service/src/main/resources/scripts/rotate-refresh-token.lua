-- KEYS[1] = refresh:{familyId}, KEYS[2] = revoked:{familyId}
-- ARGV[1] = presented jti, ARGV[2] = new jti, ARGV[3] = access-token TTL in ms (tombstone lifetime)
local current = redis.call('GET', KEYS[1])
if current == ARGV[1] then
  local ttl = redis.call('PTTL', KEYS[1])
  if ttl == nil or ttl <= 0 then
    redis.call('DEL', KEYS[1])
    redis.call('SET', KEYS[2], '1', 'PX', ARGV[3])
    return 0
  end
  redis.call('SET', KEYS[1], ARGV[2], 'PX', ttl)
  return 1
else
  redis.call('DEL', KEYS[1])
  redis.call('SET', KEYS[2], '1', 'PX', ARGV[3])
  return 0
end
