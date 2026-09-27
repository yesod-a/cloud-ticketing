-- Delete only holds owned by this order/session token.
for _, key in ipairs(KEYS) do
  if redis.call('GET', key) ~= ARGV[1] then
    return 0
  end
end
for _, key in ipairs(KEYS) do
  redis.call('DEL', key)
end
return 1
