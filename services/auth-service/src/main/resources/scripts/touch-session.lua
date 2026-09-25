-- KEYS[1] = session:{sha256(id)}
-- ARGV[1] = idle timeout in ms, ARGV[2] = absolute timeout in ms, ARGV[3] = '1' to restart idle
-- Returns {memberId, roles} for a live session, nil otherwise. Read, absolute-expiry check and
-- idle extension happen in one step so a logout or expiry can't land between them.
local fields = redis.call('HMGET', KEYS[1], 'memberId', 'roles', 'createdAt')
if not fields[1] or not fields[3] then
  return nil
end

local t = redis.call('TIME')
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
local remaining = tonumber(fields[3]) + tonumber(ARGV[2]) - now
if remaining <= 0 then
  redis.call('DEL', KEYS[1])
  return nil
end

if ARGV[3] == '1' then
  -- Never past the absolute deadline, however recent the activity.
  redis.call('PEXPIRE', KEYS[1], math.min(tonumber(ARGV[1]), remaining))
end
return {fields[1], fields[2]}
