-- KEYS[1] = login-challenge:{sha256(id)}
-- ARGV[1] = the new code, ARGV[2] = TTL in ms
-- Replaces any earlier code, starts its wrong-code count and the challenge's TTL over — the code lives
-- as long as the challenge. Returns 0 when there's no challenge.
if redis.call('EXISTS', KEYS[1]) == 0 then
  return 0
end
redis.call('HSET', KEYS[1], 'code', ARGV[1], 'attempts', 0)
redis.call('PEXPIRE', KEYS[1], ARGV[2])
return 1
