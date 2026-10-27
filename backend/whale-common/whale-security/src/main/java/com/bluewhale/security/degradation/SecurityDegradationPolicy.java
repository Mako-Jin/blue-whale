package com.bluewhale.security.degradation;

/**
 * Redis 降级安全策略（S-02）。
 *
 * <p>此前撤销键、租户状态、令牌黑名单在 Redis 不可读时统一按「未撤销/可用/未命中」放行，
 * 属于可用性优先的取舍。该取舍在 Redis 故障时会让已登出/已撤销/已停用租户的令牌继续可用。</p>
 *
 * <p>本策略提供显式开关 {@code whale.security.degradation.fail-closed}：</p>
 * <ul>
 *   <li>{@code false}（默认）：维持可用性优先，Redis 故障时放行并输出安全降级告警；</li>
 *   <li>{@code true}：安全优先，Redis 不可读时按「已撤销/已停用/已命中」拒绝请求。</li>
 * </ul>
 *
 * <p>开关由各应用启动时通过 {@link #configure(boolean)} 注入。网关与 auth 服务共用同一策略，
 * 避免两侧降级口径不一致。</p>
 *
 * @author BlueWhale
 */
public final class SecurityDegradationPolicy {

    /** 安全优先开关：Redis 不可读时是否拒绝 */
    private static volatile boolean failClosed;

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private SecurityDegradationPolicy() {
    }

    /**
     * 应用启动时注入配置
     *
     * @param value {@code true} 表示 Redis 不可读时拒绝请求
     */
    public static void configure(boolean value) {
        failClosed = value;
    }

    /**
     * 当前是否安全优先
     *
     * @return 安全优先返回 {@code true}
     */
    public static boolean failClosed() {
        return failClosed;
    }

    /**
     * 按策略合并降级返回值
     *
     * @param availabilityFirstValue 可用性优先时的返回值
     * @return 安全优先时返回该场景的「拒绝」值由调用方决定，本方法仅返回策略开关
     */
    public static boolean failClosedOr(boolean availabilityFirstValue) {
        return failClosed || availabilityFirstValue;
    }

    /**
     * 重置开关（仅供测试）
     */
    public static void reset() {
        failClosed = false;
    }
}
