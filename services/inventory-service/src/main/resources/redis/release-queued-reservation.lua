-- Release only a reservation that still owns every matching hold key.
-- KEYS: reservation hash, pending zset, inflight zset, locked bitmap, then hold keys.
-- ARGV: reservationId, token, available bitmap (unused by seated mode), nowMillis
local status = redis.call('HGET', KEYS[1], 'status')
if not status then return 0 end
if status == 'RELEASED' or status == 'FAILED' then return 1 end
for i = 5, #KEYS do
  local value = redis.call('GET', KEYS[i])
  if value and value ~= ARGV[1] .. ':' .. ARGV[2] then return 0 end
end
for i = 5, #KEYS do redis.call('DEL', KEYS[i]) end
local indexes = redis.call('HGET', KEYS[1], 'seatIndexes')
if indexes then
  for index in string.gmatch(indexes, '([^,]+)') do redis.call('SETBIT', KEYS[4], tonumber(index), 0) end
end
redis.call('HSET', KEYS[1], 'status', 'RELEASED', 'releasedAt', ARGV[4])
redis.call('ZREM', KEYS[2], ARGV[1])
redis.call('ZREM', KEYS[3], ARGV[1])
return 1
