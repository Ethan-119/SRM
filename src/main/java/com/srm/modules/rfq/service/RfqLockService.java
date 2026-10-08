package com.srm.modules.rfq.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * RFQ 工作流分布式锁（Redis SETNX）。
 * 用户级别：按「操作者 + 业务对象」加锁，同一用户对同一对象的并发操作被串行化，
 * 不同用户之间互不阻塞。
 */
@Component
@RequiredArgsConstructor
public class RfqLockService {

    private final StringRedisTemplate stringRedisTemplate;

    /** 尝试加锁，成功返回 true；已被持有返回 false */
    public boolean tryLock(String key, long ttlSeconds) {
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", ttlSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }

    /** 释放锁 */
    public void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
