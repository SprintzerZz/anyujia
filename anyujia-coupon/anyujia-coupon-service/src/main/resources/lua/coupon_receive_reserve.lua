-- KEYS[1]: coupon template hash, with {templateId} hash tag
-- KEYS[2]: user receive marker, with the same {templateId} hash tag
-- ARGV[1]: current epoch milliseconds
-- ARGV[2]: active user type
-- ARGV[3]: province code
-- ARGV[4]: city code
-- ARGV[5]: district code

if redis.call('EXISTS', KEYS[1]) == 0 then
    return 3
end

local status = tonumber(redis.call('HGET', KEYS[1], 'status'))
local validStartTime = tonumber(redis.call('HGET', KEYS[1], 'validStartTime'))
local validEndTime = tonumber(redis.call('HGET', KEYS[1], 'validEndTime'))
local applicableUserType = tonumber(redis.call('HGET', KEYS[1], 'applicableUserType'))
local scopeType = tonumber(redis.call('HGET', KEYS[1], 'scopeType') or '1')
local provinceCode = redis.call('HGET', KEYS[1], 'provinceCode') or ''
local cityCode = redis.call('HGET', KEYS[1], 'cityCode') or ''
local districtCode = redis.call('HGET', KEYS[1], 'districtCode') or ''
local now = tonumber(ARGV[1])
local userType = tonumber(ARGV[2])
local requestProvinceCode = ARGV[3] or ''
local requestCityCode = ARGV[4] or ''
local requestDistrictCode = ARGV[5] or ''

if status ~= 1 or now < validStartTime or now >= validEndTime then
    return 4
end

if applicableUserType ~= 0 and applicableUserType ~= userType then
    return 5
end

if scopeType ~= 1 then
    if provinceCode ~= '' and provinceCode ~= requestProvinceCode then
        return 5
    end
    if cityCode ~= '' and cityCode ~= requestCityCode then
        return 5
    end
    if districtCode ~= '' and districtCode ~= requestDistrictCode then
        return 5
    end
end

if redis.call('EXISTS', KEYS[2]) == 1 then
    return 2
end

local stock = tonumber(redis.call('HGET', KEYS[1], 'stock'))
if stock == nil or stock <= 0 then
    return 1
end

redis.call('SET', KEYS[2], '1', 'PX', validEndTime - now)
redis.call('HINCRBY', KEYS[1], 'stock', -1)
return 0
