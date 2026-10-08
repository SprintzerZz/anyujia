-- KEYS[1]: coupon template hash
-- KEYS[2]: user receive marker

redis.call('DEL', KEYS[2])
redis.call('DEL', KEYS[1])
return 1
