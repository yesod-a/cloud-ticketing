-- KEYS[1..3] are sold, locked, disabled bitmaps; remaining keys are temporary holds.
-- ARGV[1] token, ARGV[2] bounded TTL, ARGV[3..] stable seat indexes.
for i = 1, (#KEYS - 3) do
  local index = ARGV[i + 2]
  if redis.call('GETBIT', KEYS[1], index) == 1
      or redis.call('GETBIT', KEYS[2], index) == 1
      or redis.call('GETBIT', KEYS[3], index) == 1
      or redis.call('EXISTS', KEYS[i + 3]) == 1 then
    return 0
  end
end
for i = 4, #KEYS do
  redis.call('SET', KEYS[i], ARGV[1], 'NX', 'PX', ARGV[2])
end
return 1
