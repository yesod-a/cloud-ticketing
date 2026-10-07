-- Atomic general-admission reservation and publish journal.
-- KEYS: available, held, next-ticket, pending, reservation, idempotency
-- ARGV: reservationId, ttlMillis, nowMillis, expiresAtMillis, idempotencyTtlMillis,
--       sessionId, userId, quantity
local existing = redis.call('GET', KEYS[6])
if existing then return {2, existing} end
local quantity = tonumber(ARGV[8])
local available = tonumber(redis.call('GET', KEYS[1]) or '0')
if quantity <= 0 or available < quantity then return {0} end
local first = tonumber(redis.call('INCRBY', KEYS[3], quantity)) - quantity + 1
redis.call('DECRBY', KEYS[1], quantity)
redis.call('INCRBY', KEYS[2], quantity)
redis.call('HSET', KEYS[5],
  'reservationId', ARGV[1], 'sessionId', ARGV[6], 'userId', ARGV[7],
  'quantity', quantity, 'firstTicketNumber', first, 'lastTicketNumber', first + quantity - 1,
  'status', 'PENDING_PUBLISH', 'createdAt', ARGV[3], 'expiresAt', ARGV[4], 'attempts', '0')
redis.call('PEXPIRE', KEYS[5], ARGV[2])
redis.call('ZADD', KEYS[4], ARGV[3], ARGV[1])
redis.call('SET', KEYS[6], ARGV[1], 'PX', ARGV[5])
return {1, ARGV[1], first}
