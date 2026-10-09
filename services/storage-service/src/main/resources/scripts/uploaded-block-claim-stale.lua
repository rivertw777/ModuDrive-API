-- KEYS[1] = uploaded-blocks (sweep index); ARGV: cutoffMillis, limit
-- Read and remove in one step, so concurrent sweeps never claim the same block twice.
local members = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, ARGV[2])
if #members > 0 then
    redis.call('ZREM', KEYS[1], unpack(members))
end
return members
