-- KEYS[1] = uploaded-blocks (sweep index); ARGV: score, member...
-- Puts claimed blocks back for the next sweep. GT: a block uploaded again in the meantime already
-- sits there with a later score, and keeps it — it must not become stale again.
for i = 2, #ARGV do
    redis.call('ZADD', KEYS[1], 'GT', ARGV[1], ARGV[i])
end
return 1
