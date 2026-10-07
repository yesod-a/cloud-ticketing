local ids = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, ARGV[2])
local claimed = {}
for _, id in ipairs(ids) do
  if redis.call('ZREM', KEYS[1], id) == 1 then
    redis.call('ZADD', KEYS[2], ARGV[3], id)
    table.insert(claimed, id)
  end
end
return claimed
