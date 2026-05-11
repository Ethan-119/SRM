package com.srm.modules.system.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.srm.common.CacheConstants;
import com.srm.modules.system.entity.DictItem;
import com.srm.modules.system.mapper.DictItemMapper;
import com.srm.modules.system.service.DictItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 字典项 Service — 准静态数据，Redis Hash 长期缓存。
 * 策略：读时 Redis → miss 查 DB 回写；写时先更 DB 再删缓存。
 */
@Service
@RequiredArgsConstructor
public class DictItemServiceImpl extends ServiceImpl<DictItemMapper, DictItem> implements DictItemService {

    private final StringRedisTemplate stringRedisTemplate;

    // ==================== 读 ====================

    /**
     * 按字典类型查询 — Redis Hash 缓存。
     * Key: srm:dict:type:{dictType}
     * Hash field = id, value = JSON
     */
    @Override
    public List<DictItem> listByType(String dictType) {
        String key = CacheConstants.DICT_TYPE_KEY + dictType;

        // 1. 查 Redis Hash
        var entries = stringRedisTemplate.opsForHash().entries(key);
        if (!entries.isEmpty()) {
            return entries.values().stream()
                    .map(v -> JSONUtil.toBean(v.toString(), DictItem.class))
                    .collect(Collectors.toList());
        }

        // 2. Redis miss → DB
        List<DictItem> items = lambdaQuery()
                .eq(DictItem::getDictType, dictType)
                .orderByAsc(DictItem::getSort)
                .list();

        // 3. 回写 Redis
        if (!items.isEmpty()) {
            var hashOps = stringRedisTemplate.opsForHash();
            items.forEach(item -> hashOps.put(key, String.valueOf(item.getId()), JSONUtil.toJsonStr(item)));
            stringRedisTemplate.expire(key, Duration.ofHours(CacheConstants.DICT_TTL_HOURS));
        }

        return items;
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

    private void deleteCache(String dictType) {
        stringRedisTemplate.delete(CacheConstants.DICT_TYPE_KEY + dictType);
    }
}
