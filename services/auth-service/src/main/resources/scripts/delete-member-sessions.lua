-- KEYS[1] = member-sessions:{memberId}
-- Deletes every session in the member's index, then the index. In one step, so a login landing
-- in between can't add a session that the index then forgets.
-- The session keys come from the index, not KEYS — fine on one Redis node, not on Redis Cluster.
for _, key in ipairs(redis.call('SMEMBERS', KEYS[1])) do
  redis.call('DEL', key)
end
redis.call('DEL', KEYS[1])
return 1
