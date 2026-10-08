-- KEYS are hold keys for one order/session. The operation is all-or-nothing.
-- ARGV[1] is the base order/session token; ARGV[2] is the active TTL in ms.
for _, key in ipairs(KEYS) do
  local value = redis.call('GET', key)
  if value ~= ARGV[1] and value ~= ARGV[1] .. ':PREPARED' and value ~= ARGV[1] .. ':ACTIVE' then
    return 0
  end
end
for _, key in ipairs(KEYS) do
  redis.call('SET', key, ARGV[1] .. ':ACTIVE', 'XX', 'PX', ARGV[2])
end
return 1
