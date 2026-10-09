-- KEYS[1] = upload-count:{ownerId}; ARGV: limit, windowMillis
-- Counts one block upload against the owner's window; refuses (and takes the count back) past the
-- limit. One step, so concurrent uploads can't both slip under it, and the first one sets the TTL.
local count = redis.call('INCR', KEYS[1])
if count == 1 then
    redis.call('PEXPIRE', KEYS[1], ARGV[2])
end
if count > tonumber(ARGV[1]) then
    redis.call('DECR', KEYS[1])
    return 0
end
return 1
