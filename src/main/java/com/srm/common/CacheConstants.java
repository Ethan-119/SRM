package com.srm.common;

/**
 * Redis 缓存 Key 前缀常量
 */
public final class CacheConstants {

    private CacheConstants() {}

    /** 字典数据 — 按 dictType 分组 */
    public static final String DICT_TYPE_KEY = "srm:dict:type:";

    /** 供应商详情 */
    public static final String SUPPLIER_INFO_KEY = "srm:supplier:info:";

    /** 订单状态 */
    public static final String ORDER_STATUS_KEY = "srm:order:status:";

    /** 订单创建分布式锁 — 按 userId 防重 */
    public static final String LOCK_ORDER_CREATE = "srm:lock:order:create:";

    // ========== TTL ==========

    /** 字典缓存 — 24 小时 */
    public static final long DICT_TTL_HOURS = 24;

    /** 供应商缓存 — 1 小时 */
    public static final long SUPPLIER_TTL_HOURS = 1;

    /** 订单状态缓存 — 30 分钟 */
    public static final long ORDER_STATUS_TTL_MINUTES = 30;

    /** 防重锁 — 5 秒 */
    public static final long LOCK_TTL_SECONDS = 5;

    /** 登录 Token — 按 userId，用于单设备登录 */
    public static final String LOGIN_TOKEN_KEY = "srm:token:";

    /** Token 缓存 TTL — 与 JWT 过期时间一致 */
    public static final long TOKEN_TTL_HOURS = 24;

    // ========== 缓存防护 ==========

    /** 空值缓存 TTL — 5 分钟，防缓存穿透 */
    public static final long NULL_CACHE_TTL_MINUTES = 5;

    /** TTL 随机偏移上限 — 600 秒，防缓存雪崩 */
    public static final int TTL_JITTER_MAX_SECONDS = 600;

    /** 供应商详情互斥锁 — 防缓存击穿 */
    public static final String LOCK_SUPPLIER_INFO = "srm:lock:supplier:info:";

    /** 字典类型互斥锁 — 防缓存击穿 */
    public static final String LOCK_DICT_TYPE = "srm:lock:dict:type:";
}
