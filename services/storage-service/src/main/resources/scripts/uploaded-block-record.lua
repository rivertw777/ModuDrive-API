-- KEYS[1] = uploaded-block:{ownerId}:{hash}, KEYS[2] = uploaded-blocks (sweep index)
-- ARGV: size, ttlMillis, nowMillis, indexMember
-- One step, so the sweep never sees the index entry without the record (or the other way round).
-- ZADD moves an existing entry to now: a block uploaded again is not stale.
redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
redis.call('ZADD', KEYS[2], ARGV[3], ARGV[4])
return 1
