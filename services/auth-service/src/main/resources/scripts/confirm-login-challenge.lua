-- KEYS[1] = login-challenge:{sha256(id)}
-- ARGV[1] = the code the user typed, ARGV[2] = wrong codes allowed before the challenge ends
-- Returns {memberId, roles, email} and ends the challenge on a match, {'mismatch'} on a wrong code,
-- nil when there's no challenge. Compare and count in one step, so guesses sent together can't slip
-- past the limit.
local fields = redis.call('HMGET', KEYS[1], 'memberId', 'roles', 'email', 'code')
if not fields[4] then
  return nil
end
if fields[4] ~= ARGV[1] then
  if redis.call('HINCRBY', KEYS[1], 'attempts', 1) >= tonumber(ARGV[2]) then
    redis.call('DEL', KEYS[1])
  end
  return {'mismatch'}
end
redis.call('DEL', KEYS[1])
return {fields[1], fields[2], fields[3]}
