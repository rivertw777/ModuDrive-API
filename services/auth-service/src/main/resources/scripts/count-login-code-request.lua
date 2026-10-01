-- KEYS[1] = login-code-requests:{email}
-- KEYS[2] = login-code-cooldown:{email}
-- ARGV[1] = window in ms
-- ARGV[2] = resend cooldown in ms
-- Same rule as member-service's sign-up code requests: counts one request and returns the running
-- total, or 0 within the resend cooldown after the address's last code (not counted). The window
-- starts at the first request and is never extended.
if not redis.call('SET', KEYS[2], '1', 'NX', 'PX', ARGV[2]) then
    return 0
end
local n = redis.call('INCR', KEYS[1])
if n == 1 then
    redis.call('PEXPIRE', KEYS[1], ARGV[1])
end
return n
