-- Records bytes served against a download-quota counter.
-- KEYS[1] = download-quota:{scope}:{fileKey}
-- ARGV[1] = bytes served by this request
-- ARGV[2] = window length in seconds (applied once, on the first request of a window)
-- The INCRBY and the first-request EXPIRE are one script so a crash between them can't leave a
-- counter with no TTL (stuck forever), and concurrent requests can't each think they are first.
local first = redis.call('EXISTS', KEYS[1]) == 0
redis.call('INCRBY', KEYS[1], ARGV[1])
if first then
  redis.call('EXPIRE', KEYS[1], ARGV[2])
end
return 1
