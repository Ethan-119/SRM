package com.srm.modules.order.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.srm.common.CacheConstants;
import com.srm.common.exception.BusinessException;
import com.srm.modules.order.entity.Order;
import com.srm.modules.order.mapper.OrderMapper;
import com.srm.modules.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 采购订单 Service — 分布式锁防重 + 订单状态缓存。
 * 一人一单：Redisson 分布式锁（按 userId 串行），防止同一用户重复创建订单。
 * 状态缓存：订单状态变更时更新 Redis。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;

    // ==================== 创建订单（分布式锁防重） ====================

    @Override
    public boolean save(Order entity) {
        Long userId = entity.getCreateBy();
        if (userId == null) {
            return super.save(entity);
        }

        String lockKey = CacheConstants.LOCK_ORDER_CREATE + userId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean locked = lock.tryLock(3, CacheConstants.LOCK_TTL_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("用户 {} 订单创建锁获取失败，疑似重复提交", userId);
                throw new BusinessException(429, "操作过于频繁，请稍后重试");
            }

            boolean ok = super.save(entity);
            if (ok) {
                cacheStatus(entity);
            }
            return ok;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("系统繁忙，请稍后重试");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // ==================== 更新订单（状态缓存同步） ====================

    @Override
    public boolean updateById(Order entity) {
        boolean ok = super.updateById(entity);
        if (ok) {
            cacheStatus(entity);
        }
        return ok;
    }

    @Override
    public boolean removeById(Serializable id) {
        boolean ok = super.removeById(id);
        if (ok) {
            stringRedisTemplate.delete(CacheConstants.ORDER_STATUS_KEY + id);
        }
        return ok;
    }

    // ==================== 辅助 ====================

    private void cacheStatus(Order order) {
        if (order.getId() == null) {
            return;
        }
        String key = CacheConstants.ORDER_STATUS_KEY + order.getId();
        OrderStatusSnapshot snapshot = new OrderStatusSnapshot(order.getId(), order.getStatus());
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(snapshot),
                Duration.ofMinutes(CacheConstants.ORDER_STATUS_TTL_MINUTES));
    }

    /** 订单状态快照 — 仅缓存 status，完整数据走 DB */
    private static class OrderStatusSnapshot implements java.io.Serializable {
        private final Long id;
        private final Integer status;

        OrderStatusSnapshot(Long id, Integer status) {
            this.id = id;
            this.status = status;
        }
    }
}
