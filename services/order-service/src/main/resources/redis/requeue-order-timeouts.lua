local ids = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, ARGV[2])
local moved = 0
for _, id in ipairs(ids) do
  if redis.call('ZREM', KEYS[1], id) == 1 then
    redis.call('ZADD', KEYS[2], 0, id)
    moved = moved + 1
  end
end
return moved
