local stock=tonumber(redis.call('GET',KEYS[1]) or '-1')
if stock < 0 then return -1 end
if stock <= 0 then return -2 end
if redis.call('SISMEMBER',KEYS[2],ARGV[1])==1 then return -3 end
local count=tonumber(redis.call('GET',KEYS[3]) or '0')
local limit=tonumber(ARGV[2])
if count >= limit then return -4 end
redis.call('DECR',KEYS[1]); redis.call('SADD',KEYS[2],ARGV[1]); redis.call('INCR',KEYS[3]); return 1
