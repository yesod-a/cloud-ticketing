-- Atomically finalize a queued seated reservation.
-- KEYS: reservation hash, sold bitmap, locked bitmap, pending, inflight, hold keys
local status = redis.call('HGET', KEYS[1], 'status')
if status == 'SOLD' then return 1 end
if status == 'RELEASED' then return 0 end
for i = 6, #KEYS do
  local value = redis.call('GET', KEYS[i])
  if value and value ~= ARGV[1] .. ':' .. ARGV[1] then return 0 end
end
local indexes = redis.call('HGET', KEYS[1], 'seatIndexes')
if indexes then
  for index in string.gmatch(indexes, '([^,]+)') do
    redis.call('SETBIT', KEYS[2], tonumber(index), 1)
    redis.call('SETBIT', KEYS[3], tonumber(index), 0)
  end
end
for i = 6, #KEYS do redis.call('DEL', KEYS[i]) end
redis.call('HSET', KEYS[1], 'status', 'SOLD', 'soldAt', ARGV[2])
redis.call('ZREM', KEYS[4], ARGV[1]); redis.call('ZREM', KEYS[5], ARGV[1])
return 1
