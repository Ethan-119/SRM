package com.srm.common.util;

/**
 * 当前登录用户上下文（ThreadLocal）。
 * 由 {@code LoginInterceptor} 在鉴权通过后写入，请求结束时清理；
 * Service / MetaObjectHandler 用它取当前用户，避免依赖实体字段的时序。
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Integer> IS_ADMIN = new ThreadLocal<>();

    private UserContext() {}

    public static void set(Long userId, Integer isAdmin) {
        USER_ID.set(userId);
        IS_ADMIN.set(isAdmin);
    }

    /** 当前登录用户 ID，未登录返回 null */
    public static Long getUserId() {
        return USER_ID.get();
    }

    /** 当前用户是否管理员，未登录返回 null */
    public static Integer getIsAdmin() {
        return IS_ADMIN.get();
    }

    public static void clear() {
        USER_ID.remove();
        IS_ADMIN.remove();
    }
}
