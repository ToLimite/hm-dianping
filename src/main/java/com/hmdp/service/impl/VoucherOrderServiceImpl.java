package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1.查询优惠券id
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2.判断是否开始/结束
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("秒杀尚未开始！");
        }
        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀已经结束！");
        }
        // 3.判断库存
        if(voucher.getStock() < 1){
            return Result.fail("库存不足！");
        }

        Long userId = UserHolder.getUser().getId();

        // 先获取锁 ---> 提交事务 ---> 释放锁
        synchronized (userId.toString().intern()) {  //确保按照id的字符串值来锁！ 每次请求对象不同！每次toString生成的对象也不同！都锁不住！~
            // Spring需要代理对象来使事务生效！
            VoucherOrderServiceImpl proxy = (VoucherOrderServiceImpl) AopContext.currentProxy();  // 获取代理对象
            return proxy.createVoucherOrder(voucherId); // 注意 要从代理对象访问 才会拉起事务
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 一人一单
        Long userId = UserHolder.getUser().getId();
        // 查询订单
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if(count > 0){
            // 用户已经购买过
            return Result.fail("该用户已购买过了");
        }

        // 4.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0) // 乐观锁
                .update();
        if(!success){
            return Result.fail("库存不足！");
        }
        // 5.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 用户id
        userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);
        // 代金券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        // 6.返回订单id
        return Result.ok(orderId);
    }
}
