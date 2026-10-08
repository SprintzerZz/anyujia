-- KEYS[1]: coupon template hash
-- ARGV: stock, status, validStartTime, validEndTime, applicableUserType, ttlMillis

if redis.call('EXISTS', KEYS[1]) == 1 then
    return 0
end

redis.call('HSET', KEYS[1],
    'stock', ARGV[1],
    'status', ARGV[2],
    'validStartTime', ARGV[3],
    'validEndTime', ARGV[4],
    'applicableUserType', ARGV[5],
    'scopeType', ARGV[6],
    'provinceCode', ARGV[7],
    'cityCode', ARGV[8],
    'districtCode', ARGV[9])
redis.call('PEXPIRE', KEYS[1], ARGV[10])
return 1
