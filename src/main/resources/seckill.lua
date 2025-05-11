-- 1.参数列表
-- 1.1优惠券ID
local voucherId = ARGV[1]
-- 1.2用户ID
local userId = ARGV[2]

-- 2.数据Key
-- 2.1库存Key
local stockKey = 'seckill:stock:' .. voucherId
-- 2.2订单Key
local orderKey = 'seckill:order:' .. voucherId   -- 已下单用户ID集合

-- 3.业务逻辑
-- 3.1判断库存是否充足 get stock
if(tonumber(redis.call('get', stockKey)) <= 0) then
    return 1
end

-- 3.2判断用户是否已经购买 sismember orderKey userId
if(redis.call('sismember', orderKey, userId) == 1) then
    -- 3.3存在，说明已下单过，返回2
    return 2
end

-- 3.4扣减库存 incrby stockKey -1
redis.call('incrby', stockKey, -1)

-- 3.5下单（保存用户）sadd orderKey userId
redis.call('sadd', orderKey, userId)

return 0
