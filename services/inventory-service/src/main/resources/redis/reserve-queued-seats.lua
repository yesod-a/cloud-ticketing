-- Atomically reserve a complete seated selection and create its publish work item.
-- KEYS: sold bitmap, locked bitmap, disabled bitmap, pending zset, reservation hash,
--       idempotency key, then one hold key per requested seat.
-- ARGV: reservationId, token, ttlMillis, nowMillis, expiresAtMillis, idempotencyTtlMillis,
--       sessionId, userId, seatIndex... (one index per hold key)
local existing = redis.call('GET', KEYS[6])
if existing then return {2, existing} end

local count = #KEYS - 6
for i = 1, count do
  local index = tonumber(ARGV[8 + i])
  if redis.call('GETBIT', KEYS[1], index) == 1
      or redis.call('GETBIT', KEYS[2], index) == 1
      or redis.call('GETBIT', KEYS[3], index) == 1
      or redis.call('EXISTS', KEYS[6 + i]) == 1 then
    return {0}
  end
end

for i = 1, count do
  redis.call('SET', KEYS[6 + i], ARGV[1] .. ':' .. ARGV[2], 'PX', ARGV[3])
  redis.call('SETBIT', KEYS[2], tonumber(ARGV[8 + i]), 1)
end
local indexes = {}
for i = 1, count do indexes[i] = ARGV[8 + i] end
redis.call('HSET', KEYS[5],
  'reservationId', ARGV[1], 'sessionId', ARGV[7], 'userId', ARGV[8],
  'quantity', count, 'seatIndexes', table.concat(indexes, ','), 'status', 'PENDING_PUBLISH',
  'createdAt', ARGV[4], 'expiresAt', ARGV[5], 'attempts', '0')
redis.call('PEXPIRE', KEYS[5], ARGV[3])
redis.call('ZADD', KEYS[4], ARGV[4], ARGV[1])
redis.call('SET', KEYS[6], ARGV[1], 'PX', ARGV[6])
return {1, ARGV[1]}
