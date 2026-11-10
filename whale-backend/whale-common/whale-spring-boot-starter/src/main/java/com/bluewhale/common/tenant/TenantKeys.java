package com.bluewhale.common.tenant;

/**
 * 租户级 Redis Key 约定（Wave 5.54 Phase 2）。
 *
 * <p>统一格式 {@code whale:{tenantId}:{suffix}}，缓存为
 * {@code whale:{tenantId}:cache:{name}:{id}}，锁为 {@code whale:{tenantId}:lock:{biz}:{key}}。
 * 缺失租户上下文时租户段回落 {@code 0}（未知租户），避免不同租户共用同名 Key。</p>
 *
 * @author BlueWhale
 */
public final class TenantKeys {

    /** 全局 Key 前缀 */
    public static final String PREFIX = "whale";

    /** 分段分隔符 */
    public static final String SEPARATOR = ":";

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private TenantKeys() {
    }

    /**
     * 构造租户级 Key
     *
     * @param suffix 租户段之后的完整后缀，如 {@code cache:auth:user:perm:1}
     * @return 形如 {@code whale:{tenantId}:{suffix}} 的 Key
     */
    public static String key(String suffix) {
        return PREFIX + SEPARATOR + TenantContext.segment() + SEPARATOR + suffix;
    }

    /**
     * 构造租户级缓存 Key
     *
     * @param name 缓存名称，如 {@code auth:user:perm}
     * @param id 缓存标识，如用户 ID
     * @return 形如 {@code whale:{tenantId}:cache:{name}:{id}} 的 Key
     */
    public static String cacheKey(String name, String id) {
        return key("cache" + SEPARATOR + name + SEPARATOR + id);
    }

    /**
     * 构造指定租户的缓存 Key（不依赖当前租户上下文）。
     *
     * <p>用于跨租户失效场景（如权限点变更影响多个租户的用户）：调用方按受影响用户实际所属租户
     * 逐个构造 Key。{@code tenantId} 为空时回落未知租户段 {@code 0}。</p>
     *
     * @param tenantId 租户 ID
     * @param name 缓存名称，如 {@code auth:user:perm}
     * @param id 缓存标识，如用户 ID
     * @return 形如 {@code whale:{tenantId}:cache:{name}:{id}} 的 Key
     */
    public static String cacheKey(Long tenantId, String name, String id) {
        return key(tenantId, "cache" + SEPARATOR + name + SEPARATOR + id);
    }

    /**
     * 构造指定租户的租户级 Key（不依赖当前租户上下文）
     *
     * @param tenantId 租户 ID；为空时回落未知租户段 {@code 0}
     * @param suffix 租户段之后的完整后缀
     * @return 形如 {@code whale:{tenantId}:{suffix}} 的 Key
     */
    public static String key(Long tenantId, String suffix) {
        long segment = tenantId == null ? TenantContext.UNKNOWN_TENANT_ID : tenantId;
        return PREFIX + SEPARATOR + segment + SEPARATOR + suffix;
    }

    /**
     * 构造租户级锁 Key 前缀段
     *
     * @param prefix 锁前缀，如 {@code lock}
     * @param biz 业务标识，如 {@code auth:role}
     * @return 形如 {@code whale:{tenantId}:lock:auth:role} 的 Key 前缀
     */
    public static String lockPrefix(String prefix, String biz) {
        return key(prefix + SEPARATOR + biz);
    }
}
