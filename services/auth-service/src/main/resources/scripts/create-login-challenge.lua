-- KEYS[1] = login-challenge:{sha256(id)}
-- ARGV[1] = memberId, ARGV[2] = roles (comma-separated), ARGV[3] = email, ARGV[4] = TTL in ms
-- No code yet: one is issued once the member asks for it (issue-login-challenge-code.lua).
redis.call('HSET', KEYS[1], 'memberId', ARGV[1], 'roles', ARGV[2], 'email', ARGV[3], 'attempts', 0)
redis.call('PEXPIRE', KEYS[1], ARGV[4])
return 1
