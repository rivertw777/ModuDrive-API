-- KEYS[1] = login-challenge:{sha256(id)}
-- ARGV[1] = the code the user typed, ARGV[2] = wrong codes allowed before the code ends
-- Returns {memberId, roles, email} and ends the challenge on a match, {'mismatch'} on a wrong code,
-- (a guess before any code was sent counts as wrong), {'exhausted', email} once the wrong codes are used up (by this
-- guess or an earlier one — the challenge stays, so a resend is enough), nil when there's no challenge
-- (expired). Compare and count in one step, so guesses sent together can't slip past the limit.
local fields = redis.call('HMGET', KEYS[1], 'memberId', 'roles', 'email', 'code', 'attempts')
if not fields[1] then
  return nil
end
if tonumber(fields[5] or '0') >= tonumber(ARGV[2]) then
  return {'exhausted', fields[3]}
end
if fields[4] ~= ARGV[1] then
  if redis.call('HINCRBY', KEYS[1], 'attempts', 1) >= tonumber(ARGV[2]) then
    return {'exhausted', fields[3]}
  end
  return {'mismatch'}
end
redis.call('DEL', KEYS[1])
return {fields[1], fields[2], fields[3]}
