package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ShopType> getList() {
        String key = CACHE_SHOP_TYPE_KEY;
        List<ShopType> list = new ArrayList<>();
        List<String> listString = stringRedisTemplate.opsForList().range(key, 0, -1);
        if(listString != null && !listString.isEmpty()){
            for(String shopTypeString: listString){
                ShopType shopType = JSONUtil.toBean(shopTypeString, ShopType.class);
                list.add(shopType);
            }
            return list;
        }
        list = query().orderByAsc("sort").list();
        if(list != null){
            for(ShopType shopType: list){
                String jsonStr = JSONUtil.toJsonStr(shopType);
                stringRedisTemplate.opsForList().rightPush(key, jsonStr);
            }
        }
        return list;
    }
}
