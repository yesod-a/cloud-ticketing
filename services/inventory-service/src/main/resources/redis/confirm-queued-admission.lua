local status = redis.call('HGET', KEYS[1], 'status')
if status == 'SOLD' then return 1 end
if status == 'RELEASED' then return 0 end
local quantity = tonumber(ARGV[2])
redis.call('DECRBY', KEYS[2], quantity)
redis.call('INCRBY', KEYS[3], quantity)
redis.call('HSET', KEYS[1], 'status', 'SOLD', 'soldAt', ARGV[3])
redis.call('ZREM', KEYS[4], ARGV[1]); redis.call('ZREM', KEYS[5], ARGV[1])
return 1
