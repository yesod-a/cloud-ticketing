-- KEYS[1] is the ready pointer; KEYS[2..4] are sold, locked, disabled bitmaps.
-- ARGV[1] token, ARGV[2] bounded TTL, ARGV[3..] stable seat indexes.
if redis.call('GET', KEYS[1]) == false or redis.call('GET', KEYS[1]) == '0' then
  return 0
end
for i = 1, (#KEYS - 4) do
  local index = ARGV[i + 2]
  if redis.call('GETBIT', KEYS[2], index) == 1
      or redis.call('GETBIT', KEYS[3], index) == 1
      or redis.call('GETBIT', KEYS[4], index) == 1
      or redis.call('EXISTS', KEYS[i + 4]) == 1 then
    return 0
  end
end
for i = 5, #KEYS do
  redis.call('SET', KEYS[i], ARGV[1], 'NX', 'PX', ARGV[2])
end
return 1
