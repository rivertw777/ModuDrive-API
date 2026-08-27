-- KEYS[1] = refresh:{familyId}, KEYS[2] = revoked:{familyId}, KEYS[3] = prev:{familyId}
-- ARGV[1] = presented jti, ARGV[2] = new jti, ARGV[3] = access-token TTL in ms (tombstone lifetime)
-- ARGV[4] = grace window TTL in ms (how long a just-rotated-away jti is still honored)
local current = redis.call('GET', KEYS[1])
local matched = current == ARGV[1]
if not matched then
  -- The presented jti isn't current, but it may be the one a sibling request (same browser,
  -- concurrent tabs, or a retried request) just rotated away moments ago — not an actual replay
  -- by an attacker holding a stale token. Honoring it here means neither request destroys the
  -- family (#207).
  local prev = redis.call('GET', KEYS[3])
  matched = prev ~= false and prev == ARGV[1]
end

if matched then
  local ttl = redis.call('PTTL', KEYS[1])
  if ttl == nil or ttl <= 0 then
    redis.call('DEL', KEYS[1])
    redis.call('SET', KEYS[2], '1', 'PX', ARGV[3])
    return 0
  end
  redis.call('SET', KEYS[3], current, 'PX', ARGV[4])
  redis.call('SET', KEYS[1], ARGV[2], 'PX', ttl)
  return 1
else
  redis.call('DEL', KEYS[1])
  redis.call('SET', KEYS[2], '1', 'PX', ARGV[3])
  return 0
end
