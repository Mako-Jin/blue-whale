package com.bluewhale.security.token;

/**
 * 租户状态缓存键约定（Wave 5.54 Phase 2）。
 *
 * <p>whale-service-auth 在租户开通/启停/续期时写入，网关在验签后读取，用于即时拒绝停用或
 * 已到期租户的存量 accessToken；键格式与 {@code TenantKeys} 保持一致：</p>
 * <ul>
 *   <li>状态：{@code whale:{tenantId}:cache:auth:tenant:status}</li>
 *   <li>到期时间（Unix 秒，缺失表示长期有效）：{@code whale:{tenantId}:cache:auth:tenant:expire}</li>
 * </ul>
 *
 * @author BlueWhale
 */
public final class TenantStatusKeys {

    /** 启用状态值 */
    public static final String ENABLED = "1";

    /** 停用状态值 */
    public static final String DISABLED = "0";

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private TenantStatusKeys() {
    }

    /**
     * 构造租户状态缓存键
     *
     * @param tenantId 租户 ID
     * @return 缓存键
     */
    public static String statusKey(Long tenantId) {
        return "whale:" + tenantId + ":cache:auth:tenant:status";
    }

    /**
     * 构造租户到期时间缓存键
     *
     * @param tenantId 租户 ID
     * @return 缓存键
     */
    public static String expireKey(Long tenantId) {
        return "whale:" + tenantId + ":cache:auth:tenant:expire";
    }
}
