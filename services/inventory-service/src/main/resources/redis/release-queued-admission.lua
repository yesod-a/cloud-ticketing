-- Idempotently release a general-admission reservation.
-- KEYS: available, held, reservation, pending, inflight
-- ARGV: reservationId, quantity, nowMillis
local status = redis.call('HGET', KEYS[3], 'status')
if not status then return 0 end
if status == 'RELEASED' then return 1 end
if status == 'SOLD' then return 0 end
local quantity = tonumber(ARGV[2])
redis.call('INCRBY', KEYS[1], quantity)
redis.call('DECRBY', KEYS[2], quantity)
redis.call('HSET', KEYS[3], 'status', 'RELEASED', 'releasedAt', ARGV[3])
redis.call('ZREM', KEYS[4], ARGV[1])
redis.call('ZREM', KEYS[5], ARGV[1])
return 1
