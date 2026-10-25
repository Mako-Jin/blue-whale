package com.bluewhale.common.tenant;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

/**
 * 当前请求租户上下文（Wave 5.54 Phase 2）。
 *
 * <p>由 {@code TenantContextFilter} 从网关可信注入的 {@code X-Tenant-Id} 请求头写入，
 * 请求结束即清理；异步线程、MQ 消费与内部调用通过 {@link TenantContextTaskDecorator}
 * 或显式 {@link #set(Long)} 传递。</p>
 *
 * <p><b>失败关闭</b>：需要租户上下文的读写在缺失上下文时直接 400，不回落默认租户。</p>
 *
 * @author BlueWhale
 */
public final class TenantContext {

    /** 未知租户兜底值：仅用于日志/审计等不允许空租户的落库场景 */
    public static final long UNKNOWN_TENANT_ID = 0L;

    private static final ThreadLocal<Long> HOLDER = new ThreadLocal<>();

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private TenantContext() {
    }

    /**
     * 绑定当前线程租户
     *
     * @param tenantId 租户 ID
     */
    public static void set(Long tenantId) {
        if (tenantId == null) {
            HOLDER.remove();
            return;
        }
        HOLDER.set(tenantId);
    }

    /**
     * 获取当前线程租户（可能为空）
     *
     * @return 租户 ID；未绑定时返回 {@code null}
     */
    public static Long get() {
        return HOLDER.get();
    }

    /**
     * 获取当前线程租户，缺失时抛出参数异常
     *
     * @return 租户 ID
     */
    public static Long requireId() {
        Long tenantId = HOLDER.get();
        if (tenantId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "缺少租户上下文，请通过网关访问");
        }
        return tenantId;
    }

    /**
     * 获取用于 Redis Key / 对象键的租户段：缺失时返回 {@code 0}（未知租户）
     *
     * <p>仅用于日志、缓存键等必须生成字符串的场景；业务数据读写必须使用 {@link #requireId()}。</p>
     *
     * @return 租户段文本
     */
    public static String segment() {
        Long tenantId = HOLDER.get();
        return tenantId == null ? String.valueOf(UNKNOWN_TENANT_ID) : tenantId.toString();
    }

    /**
     * 清理当前线程租户上下文
     */
    public static void clear() {
        HOLDER.remove();
    }
}
