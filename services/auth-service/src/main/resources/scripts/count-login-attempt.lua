-- KEYS[1] = login-attempts:{email}
-- ARGV[1] = window in ms
-- Counts one attempt and returns the running total. The window starts at the first attempt and is
-- never extended, so a locked account unlocks when that window ends however often it is retried.
local n = redis.call('INCR', KEYS[1])
if n == 1 then
    redis.call('PEXPIRE', KEYS[1], ARGV[1])
end
return n
