package com.bluewhale.auth.domain.tenant.aggregate;

/**
 * Tenant 聚合根（对应 {@code sys_tenant}，Wave 5.54 Phase 0）。
 *
 * <p><b>不变量</b>：① {@code tenantCode} 非空；② 仅 {@code status=1} 的租户可用于登录与签发令牌。</p>
 * <p>本类为纯领域对象，不依赖 Spring / MyBatis / Web 等框架类型。</p>
 *
 * @author BlueWhale
 */
public class Tenant {

    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;
    /** 状态：停用 */
    public static final int STATUS_DISABLED = 0;

    private final Long id;
    private final String tenantCode;
    private final String tenantName;
    private final int status;

    /**
     * 构造器：仅允许通过 {@link #reconstitute(Long, String, String, Integer)} 重建聚合
     *
     * @param id 租户 ID
     * @param tenantCode 租户编码
     * @param tenantName 租户名称
     * @param status 状态
     */
    private Tenant(Long id, String tenantCode, String tenantName, int status) {
        this.id = id;
        this.tenantCode = tenantCode;
        this.tenantName = tenantName;
        this.status = status;
    }

    /**
     * 由持久化数据重建聚合（不做格式校验，保持存量数据可读）
     *
     * @param id 租户 ID
     * @param tenantCode 租户编码
     * @param tenantName 租户名称
     * @param status 状态（1 启用 / 0 停用），{@code null} 按停用处理
     * @return 租户聚合
     */
    public static Tenant reconstitute(Long id, String tenantCode, String tenantName, Integer status) {
        return new Tenant(id, tenantCode, tenantName,
                status == null ? STATUS_DISABLED : status);
    }

    /**
     * 判断租户是否启用
     *
     * @return 启用返回 {@code true}
     */
    public boolean isEnabled() {
        return status == STATUS_ENABLED;
    }

    /**
     * 获取租户 ID
     *
     * @return 租户 ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 获取租户编码
     *
     * @return 租户编码
     */
    public String getTenantCode() {
        return tenantCode;
    }

    /**
     * 获取租户名称
     *
     * @return 租户名称
     */
    public String getTenantName() {
        return tenantName;
    }

    /**
     * 获取租户状态
     *
     * @return 状态（1 启用 / 0 停用）
     */
    public int getStatus() {
        return status;
    }
}
