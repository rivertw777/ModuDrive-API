-- KEYS[1] = login-challenge:{sha256(id)}
-- ARGV[1] = memberId, ARGV[2] = roles (comma-separated), ARGV[3] = email, ARGV[4] = code,
-- ARGV[5] = TTL in ms
redis.call('HSET', KEYS[1], 'memberId', ARGV[1], 'roles', ARGV[2], 'email', ARGV[3], 'code', ARGV[4], 'attempts', 0)
redis.call('PEXPIRE', KEYS[1], ARGV[5])
return 1
