-- KEYS[1]: coupon template hash
-- KEYS[2]: user receive marker

if redis.call('EXISTS', KEYS[2]) == 0 then
    return 0
end

if redis.call('EXISTS', KEYS[1]) == 1 then
    redis.call('HINCRBY', KEYS[1], 'stock', 1)
end
return 1
