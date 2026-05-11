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
}
