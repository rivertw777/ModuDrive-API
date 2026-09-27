-- KEYS[1] = email-verify-code:{email}, KEYS[2] = email-verify-attempts:{email}
-- ARGV[1] = the code the user typed, ARGV[2] = wrong codes allowed before the code ends,
-- ARGV[3] = attempts TTL in ms
-- Returns 1 and ends the code on a match, 0 on a wrong or missing code. Compare and count in one
-- step, so guesses sent together can't slip past the limit.
local stored = redis.call('GET', KEYS[1])
if not stored then
  return 0
end
if stored == ARGV[1] then
  redis.call('DEL', KEYS[1], KEYS[2])
  return 1
end
local n = redis.call('INCR', KEYS[2])
if n == 1 then
  redis.call('PEXPIRE', KEYS[2], ARGV[3])
end
if n >= tonumber(ARGV[2]) then
  redis.call('DEL', KEYS[1])
end
return 0
