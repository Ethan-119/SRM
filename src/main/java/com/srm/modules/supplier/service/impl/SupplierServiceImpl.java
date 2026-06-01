package com.srm.modules.supplier.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.srm.common.CacheConstants;
import com.srm.modules.supplier.dto.SupplierPageDTO;
import com.srm.modules.supplier.entity.Supplier;
import com.srm.modules.supplier.mapper.SupplierMapper;
import com.srm.modules.supplier.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 供应商 Service — 缓存防护：穿透（空值缓存）、击穿（互斥锁）、雪崩（TTL随机化）
 */
@Service
@RequiredArgsConstructor
public class SupplierServiceImpl extends ServiceImpl<SupplierMapper, Supplier> implements SupplierService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;

    private static final Random TTL_RANDOM = new Random();

    // ==================== 读 ====================

    @Override
    public IPage<Supplier> pageList(SupplierPageDTO queryDTO) {
        long pageNum = 1L;
        long pageSize = 10L;
        if (queryDTO != null) {
            if (queryDTO.getPageNum() != null && queryDTO.getPageNum() > 0) {
                pageNum = queryDTO.getPageNum();
            }
            if (queryDTO.getPageSize() != null && queryDTO.getPageSize() > 0) {
                pageSize = queryDTO.getPageSize();
            }
        }
        Page<Supplier> page = new Page<>(pageNum, pageSize);
        return baseMapper.pageList(page, queryDTO);
    }

    @Override
    public Supplier getById(Serializable id) {
        String key = CacheConstants.SUPPLIER_INFO_KEY + id;

        // 1. 查 Redis
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null) {
            if (json.isEmpty()) {
                return null; // 空值缓存命中，防穿透
            }
            return JSONUtil.toBean(json, Supplier.class);
        }

        // 2. 缓存 miss → 加互斥锁，防击穿
        String lockKey = CacheConstants.LOCK_SUPPLIER_INFO + id;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean locked = lock.tryLock(3, 5, TimeUnit.SECONDS);
            if (locked) {
                try {
                    // 双重检查
                    json = stringRedisTemplate.opsForValue().get(key);
                    if (json != null) {
                        if (json.isEmpty()) {
                            return null;
                        }
                        return JSONUtil.toBean(json, Supplier.class);
                    }

                    // 查 DB
                    Supplier supplier = super.getById(id);
                    if (supplier != null && isActive(supplier.getStatus())) {
                        long ttl = CacheConstants.SUPPLIER_TTL_HOURS * 3600;
                        ttl += TTL_RANDOM.nextInt(CacheConstants.TTL_JITTER_MAX_SECONDS);
                        stringRedisTemplate.opsForValue().set(
                                key, JSONUtil.toJsonStr(supplier), Duration.ofSeconds(ttl));
                    } else {
                        // 空值缓存，防穿透
                        stringRedisTemplate.opsForValue().set(
                                key, "", Duration.ofMinutes(CacheConstants.NULL_CACHE_TTL_MINUTES));
                    }
                    return supplier;
                } finally {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 获取锁失败 → 短暂等待后重试读缓存
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        json = stringRedisTemplate.opsForValue().get(key);
        if (json != null && !json.isEmpty()) {
            return JSONUtil.toBean(json, Supplier.class);
        }
        return null;
    }

    // ==================== 写：先更 DB，再删缓存 ====================

    @Override
    public boolean save(Supplier entity) {
        boolean ok = super.save(entity);
        if (ok && entity.getId() != null && isActive(entity.getStatus())) {
            String key = CacheConstants.SUPPLIER_INFO_KEY + entity.getId();
            long ttl = CacheConstants.SUPPLIER_TTL_HOURS * 3600;
            ttl += TTL_RANDOM.nextInt(CacheConstants.TTL_JITTER_MAX_SECONDS);
            stringRedisTemplate.opsForValue().set(
                    key, JSONUtil.toJsonStr(entity), Duration.ofSeconds(ttl));
        }
        return ok;
    }

    @Override
    public boolean updateById(Supplier entity) {
        boolean ok = super.updateById(entity);
        if (ok) {
            deleteCache(entity.getId());
        }
        return ok;
    }

    @Override
    public boolean removeById(Serializable id) {
        boolean ok = super.removeById(id);
        if (ok) {
            deleteCache(id);
        }
        return ok;
    }

    // ==================== 辅助 ====================

    /** 状态: 2-已准入 3-合作中 */
    private boolean isActive(Integer status) {
        return status != null && (status == 2 || status == 3);
    }

    private void deleteCache(Serializable id) {
        stringRedisTemplate.delete(CacheConstants.SUPPLIER_INFO_KEY + id);
    }
}
