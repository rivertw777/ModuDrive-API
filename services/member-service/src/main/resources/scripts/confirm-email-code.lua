-- KEYS[1] = email-verify-code:{email}, KEYS[2] = email-verify-attempts:{email},
-- KEYS[3] = email-verify-cooldown:{email}
-- ARGV[1] = the code the user typed, ARGV[2] = wrong codes allowed before the code ends,
-- ARGV[3] = attempts TTL in ms
-- Returns 1 on a match, 0 on a wrong code that can still be retried, -1 when there is no code (expired
-- or never sent), -2 once the wrong
-- codes are used up (by this guess or an earlier one — the code is kept until it expires so later
-- guesses still get this answer). Once the code can't be used the resend cooldown goes with it, so a
-- new code can be asked for at once. Compare and count in one step, so guesses sent together can't
-- slip past the limit.
local stored = redis.call('GET', KEYS[1])
if not stored then
  redis.call('DEL', KEYS[3])
  return -1
end
if tonumber(redis.call('GET', KEYS[2]) or '0') >= tonumber(ARGV[2]) then
  redis.call('DEL', KEYS[3])
  return -2
end
if stored == ARGV[1] then
  redis.call('DEL', KEYS[1], KEYS[2], KEYS[3])
  return 1
end
local n = redis.call('INCR', KEYS[2])
if n == 1 then
  redis.call('PEXPIRE', KEYS[2], ARGV[3])
end
if n >= tonumber(ARGV[2]) then
  redis.call('DEL', KEYS[3])
  return -2
end
return 0
