package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        //Shop shop = queryWithPassThrough(id);

        //工具类 穿透
        //Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 缓存击穿
        //Shop shop = queryWithMutex(id);

        //工具类 击穿
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 逻辑过期
        //Shop shop = queryWithLogicalExpire(id);

        //前端友好性处理
        if(shop == null){
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }

//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
//
//    public Shop queryWithLogicalExpire(Long id){
//        String key = CACHE_SHOP_KEY + id;
//        // 1.从redis中查询店铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        // 2.判断是否存在
//        if(StrUtil.isBlank(shopJson)){
//            // 3.未命中，直接返回
//            return null;
//        }
//        // 4.命中，json反序列化为对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        // 5.判断是否过期
//        if(expireTime.isAfter(LocalDateTime.now())){
//            // 5.1未过期，直接返回店铺信息
//            return shop;
//        }
//
//        // 5.2已过期，需要缓存重建
//        // 6.缓存重建
//        // 6.1获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        boolean isLock = tryLock(lockKey);
//        // 6.2判断是否成功获取锁
//        if (isLock) {
//            // 6.3成功，开启独立线程，实现缓存重建
//            String shopJsonTMP = stringRedisTemplate.opsForValue().get(key);
//            // 2.判断是否过期
//            RedisData redisDataTMP = JSONUtil.toBean(shopJsonTMP, RedisData.class);
//            Shop shopTMP = JSONUtil.toBean((JSONObject) redisDataTMP.getData(), Shop.class);
//            LocalDateTime expireTimeTMP = redisDataTMP.getExpireTime();
//            // 没过期（redis数据已经被更新）
//            if(expireTimeTMP.isAfter(LocalDateTime.now())){
//                return shopTMP;
//            }
//            // 过期（redis数据需要更新）
//            else{
//                CACHE_REBUILD_EXECUTOR.submit(() ->{
//                    try {
//                        // 重建缓存
//                        saveShop2Redis(id, 20L);
//                    } catch (Exception e) {
//                        throw new RuntimeException(e);
//                    } finally {
//                        // 释放锁
//                        unlock(lockKey);
//                    }
//                });
//            }
//        }
//        // 6.4店铺信息
//        return shop;
//    }

    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        // 1.查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        // 2.封装过期数据
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 3.写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("店铺id不能为空");
        }
        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

//    // 缓存击穿
//    public Shop queryWithMutex(Long id){
//        String key = CACHE_SHOP_KEY + id;
//        // 1.从redis中查询店铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        // 2.判断是否存在
//        if(StrUtil.isNotBlank(shopJson)){
//            // 3.存在，直接返回
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        if(shopJson != null){  // shopJon == ""
//            // 返回错误信息
//            return null;
//        }
//
//        // 4.实现缓存重建
//        // 4.1获取互斥锁
//        String lockKey = "lock:shop:" + id;
//        Shop shop = null;
//        try {
//            boolean isLock = tryLock(lockKey);
//            // 4.2判断是否成功
//            if(!isLock){
//                // 4.3失败：休眠、重试
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//            // 4.4成功(获取到锁)：根据id查数据库
//            // 先检查redis中有无店铺信息了
//            String shopJsonTMP = stringRedisTemplate.opsForValue().get(key);
//            if(StrUtil.isNotBlank(shopJsonTMP)){
//                return JSONUtil.toBean(shopJsonTMP, Shop.class);
//            }
//            if(shopJsonTMP != null){  // shopJonTMP == ""
//                return null;
//            }
//            // 若缓存中没有查到数据（查出来的数据也不是""），说明需要查数据库
//            shop = getById(id);
//            // 模拟重建超时
//            Thread.sleep(200);
//            // 5.数据库中不存在 返回错误
//            if(shop == null){
//                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            // 6.数据库中存在，写入redis
//            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            // 7.释放互斥锁
//            unlock(lockKey);
//        }
//        // 7.返回Result
//        return shop;
//    }

    // 缓存穿透（only）（备份） （防止恶意不存在数据的访问）
//    public Shop queryWithPassThrough(Long id){
//        String key = CACHE_SHOP_KEY + id;
//        // 1.从redis中查询店铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        // 2.判断是否存在
//        if(StrUtil.isNotBlank(shopJson)){
//            // 3.存在，直接返回
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        if(shopJson != null){  // shopJon == ""
//            // 返回错误信息
//            return null;
//        }
//        // 4.不存在 根据id查询数据库
//        Shop shop = getById(id);
//        // 5.数据库中不存在 返回错误
//        if(shop == null){
//            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//        }
//        // 6.数据库中存在，写入redis
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        // 7.返回Result
//        return shop;
//    }

//    // 尝试获取锁
//    private boolean tryLock(String key){
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//
//    private void unlock(String key){
//        stringRedisTemplate.delete(key);
//    }

}