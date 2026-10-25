package com.bluewhale.auth.security;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

/**
 * 当前请求租户上下文（Wave 5.54 Phase 1）。
 *
 * <p>由 {@link TenantContextFilter} 从网关可信注入的 {@code X-Tenant-Id} 请求头写入，
 * 请求结束即清理。仓储层据此对 {@code sys_user}/{@code sys_role}/{@code sys_menu}/{@code sys_dept}
 * 追加租户条件，避免应用层逐层透传租户参数。</p>
 *
 * <p><b>失败关闭</b>：需要租户上下文的读写在缺失上下文时直接 400，不回落默认租户。</p>
 *
 * @author BlueWhale
 */
public final class TenantContext {

    private static final ThreadLocal<Long> HOLDER = new ThreadLocal<>();

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private TenantContext() {
    }

    /**
     * 绑定当前请求租户
     *
     * @param tenantId 租户 ID
     */
    public static void set(Long tenantId) {
        HOLDER.set(tenantId);
    }

    /**
     * 获取当前请求租户（可能为空）
     *
     * @return 租户 ID；未绑定时返回 {@code null}
     */
    public static Long get() {
        return HOLDER.get();
    }

    /**
     * 获取当前请求租户，缺失时抛出参数异常
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
     * 清理当前线程租户上下文
     */
    public static void clear() {
        HOLDER.remove();
    }
}
