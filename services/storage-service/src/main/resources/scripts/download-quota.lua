-- KEYS[1] = download-quota:{scope}:{fileKey}
-- ARGV[1] = bytes served by this request
-- ARGV[2] = window length in seconds (applied once, on the first request of a window)
-- ARGV[3] = per-file byte quota for one window
-- returns 1 while the file is still within quota, 0 once it is over
local first = redis.call('EXISTS', KEYS[1]) == 0
local total = redis.call('INCRBY', KEYS[1], ARGV[1])
if first then
  redis.call('EXPIRE', KEYS[1], ARGV[2])
  -- The opening request of a window always goes through, even a file larger than the whole
  -- quota — otherwise such a file would be permanently undownloadable for the window.
  return 1
end
if total > tonumber(ARGV[3]) then
  return 0
end
return 1
