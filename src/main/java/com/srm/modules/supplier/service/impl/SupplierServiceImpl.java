package com.srm.modules.supplier.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.srm.common.CacheConstants;
import com.srm.modules.supplier.entity.Supplier;
import com.srm.modules.supplier.mapper.SupplierMapper;
import com.srm.modules.supplier.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.Duration;

/**
 * 供应商 Service — 高频查询，String(JSON) 缓存。
 * 策略：仅缓存"合作中/已准入"的供应商，减少 Redis 内存。
 * 读写：读 Redis → miss DB 回写；写时先更 DB 再删缓存。
 */
@Service
@RequiredArgsConstructor
public class SupplierServiceImpl extends ServiceImpl<SupplierMapper, Supplier> implements SupplierService {

    private final StringRedisTemplate stringRedisTemplate;

    // ==================== 读 ====================

    @Override
    public Supplier getById(Serializable id) {
        String key = CacheConstants.SUPPLIER_INFO_KEY + id;

        // 1. Redis
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null) {
            return JSONUtil.toBean(json, Supplier.class);
        }

        // 2. Redis miss → DB
        Supplier supplier = super.getById(id);
        if (supplier != null && isActive(supplier.getStatus())) {
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(supplier),
                    Duration.ofHours(CacheConstants.SUPPLIER_TTL_HOURS));
        }

        return supplier;
    }

    // ==================== 写：先更 DB，再删缓存 ====================

    @Override
    public boolean save(Supplier entity) {
        boolean ok = super.save(entity);
        if (ok && entity.getId() != null && isActive(entity.getStatus())) {
            String key = CacheConstants.SUPPLIER_INFO_KEY + entity.getId();
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(entity),
                    Duration.ofHours(CacheConstants.SUPPLIER_TTL_HOURS));
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
