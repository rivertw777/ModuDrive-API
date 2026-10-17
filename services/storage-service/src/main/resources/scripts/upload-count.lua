-- KEYS[1] = upload-count:{ownerId}; ARGV: limit, windowMillis, count
-- Counts a request's block uploads against the owner's window; refuses them all (and takes the
-- count back) if they'd go past the limit. One step, so concurrent uploads can't both slip under
-- it, and the first one sets the TTL.
local count = redis.call('INCRBY', KEYS[1], ARGV[3])
if count == tonumber(ARGV[3]) then
    redis.call('PEXPIRE', KEYS[1], ARGV[2])
end
if count > tonumber(ARGV[1]) then
    redis.call('DECRBY', KEYS[1], ARGV[3])
    return 0
end
return 1
