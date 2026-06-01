package com.srm.modules.system.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.srm.common.CacheConstants;
import com.srm.modules.system.entity.DictItem;
import com.srm.modules.system.mapper.DictItemMapper;
import com.srm.modules.system.service.DictItemService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 字典项 Service — 准静态数据，Redis Hash 长期缓存。
 * 缓存防护：穿透（空标记）、击穿（互斥锁）、雪崩（TTL随机化）
 */
@Service
@RequiredArgsConstructor
public class DictItemServiceImpl extends ServiceImpl<DictItemMapper, DictItem> implements DictItemService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;

    private static final Random TTL_RANDOM = new Random();

    /** Hash 中用于标记"无数据"的字段名 */
    private static final String NULL_MARKER = "__null__";

    // ==================== 读 ====================

    /**
     * 按字典类型查询 — Redis Hash 缓存。
     */
    @Override
    public List<DictItem> listByType(String dictType) {
        String key = CacheConstants.DICT_TYPE_KEY + dictType;

        // 1. 查 Redis Hash
        List<DictItem> cached = readHashCache(key);
        if (cached != null) {
            return cached;
        }

        // 2. 缓存 miss → 加互斥锁，防击穿
        String lockKey = CacheConstants.LOCK_DICT_TYPE + dictType;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean locked = lock.tryLock(3, 5, TimeUnit.SECONDS);
            if (locked) {
                try {
                    // 双重检查
                    cached = readHashCache(key);
                    if (cached != null) {
                        return cached;
                    }

                    // 查 DB
                    List<DictItem> items = lambdaQuery()
                            .eq(DictItem::getDictType, dictType)
                            .orderByAsc(DictItem::getSort)
                            .list();

                    // 回写 Redis
                    if (!items.isEmpty()) {
                        writeHashCache(key, items);
                    } else {
                        // 空值标记，防穿透
                        stringRedisTemplate.opsForHash().put(key, NULL_MARKER, "1");
                        stringRedisTemplate.expire(key,
                                Duration.ofMinutes(CacheConstants.NULL_CACHE_TTL_MINUTES));
                    }

                    return items;
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
        cached = readHashCache(key);
        if (cached != null) {
            return cached;
        }
        return new ArrayList<>();
    }

    // ==================== 写：先更 DB，再删缓存 ====================

    @Override
    public boolean save(DictItem entity) {
        boolean ok = super.save(entity);
        if (ok) {
            deleteCache(entity.getDictType());
        }
        return ok;
    }

    @Override
    public boolean updateById(DictItem entity) {
        DictItem old = super.getById(entity.getId());
        boolean ok = super.updateById(entity);
        if (ok) {
            deleteCache(entity.getDictType());
            if (old != null && !old.getDictType().equals(entity.getDictType())) {
                deleteCache(old.getDictType());
            }
        }
        return ok;
    }

    @Override
    public boolean removeById(Serializable id) {
        DictItem entity = super.getById(id);
        boolean ok = super.removeById(id);
        if (ok && entity != null) {
            deleteCache(entity.getDictType());
        }
        return ok;
    }

    // ==================== 辅助 ====================

    /**
     * 从 Hash 读取缓存。返回 null 表示 miss，空列表表示命中空标记。
     */
    private List<DictItem> readHashCache(String key) {
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        if (entries.isEmpty()) {
            return null; // miss
        }
        if (entries.size() == 1 && entries.containsKey(NULL_MARKER)) {
            return Collections.emptyList(); // 空标记命中，防穿透
        }
        List<DictItem> result = new ArrayList<>();
        for (Object value : entries.values()) {
            result.add(JSONUtil.toBean(String.valueOf(value), DictItem.class));
        }
        return result;
    }

    /**
     * 写入 Hash 缓存，含随机 TTL 偏移（防雪崩）。
     */
    private void writeHashCache(String key, List<DictItem> items) {
        var hashOps = stringRedisTemplate.opsForHash();
        for (DictItem item : items) {
            hashOps.put(key, String.valueOf(item.getId()), JSONUtil.toJsonStr(item));
        }
        long ttl = CacheConstants.DICT_TTL_HOURS * 3600;
        ttl += TTL_RANDOM.nextInt(CacheConstants.TTL_JITTER_MAX_SECONDS);
        stringRedisTemplate.expire(key, Duration.ofSeconds(ttl));
    }

    private void deleteCache(String dictType) {
        stringRedisTemplate.delete(CacheConstants.DICT_TYPE_KEY + dictType);
    }
}
