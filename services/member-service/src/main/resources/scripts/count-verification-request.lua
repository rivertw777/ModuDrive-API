-- KEYS[1] = email-verify-requests:{email}
-- KEYS[2] = email-verify-cooldown:{email}
-- ARGV[1] = window in ms
-- ARGV[2] = resend cooldown in ms
-- Counts one code request and returns the running total, or 0 within the resend cooldown after the
-- address's last code (not counted). The window starts at the first request and is never extended, so a
-- blocked address unblocks when that window ends however often it is retried.
if not redis.call('SET', KEYS[2], '1', 'NX', 'PX', ARGV[2]) then
    return 0
end
local n = redis.call('INCR', KEYS[1])
if n == 1 then
    redis.call('PEXPIRE', KEYS[1], ARGV[1])
end
return n
