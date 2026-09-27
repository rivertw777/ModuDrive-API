-- KEYS[1] = email-verify-requests:{email}
-- ARGV[1] = window in ms
-- Counts one code request and returns the running total. The window starts at the first request and
-- is never extended, so a blocked address unblocks when that window ends however often it is retried.
local n = redis.call('INCR', KEYS[1])
if n == 1 then
    redis.call('PEXPIRE', KEYS[1], ARGV[1])
end
return n
