package com.bluewhale.auth.domain.repository;

import java.util.List;

/**
 * 用户可进入租户关系仓储端口（Wave 5.54 Phase 5.1：一人多租户）。
 *
 * <p>本端口只回答「该用户可进入哪些租户」，不放宽任何业务数据的租户过滤；
 * 关系表跨租户，所有查询必须显式带 {@code userId}。</p>
 *
 * @author BlueWhale
 */
public interface UserTenantRepository {

    /**
     * 判断用户与租户之间是否存在「启用」关系
     *
     * @param userId 用户 ID
     * @param tenantId 租户 ID
     * @return 存在且启用返回 true，否则返回 false
     */
    boolean existsEnabled(Long userId, Long tenantId);

    /**
     * 查询用户可进入的启用租户 ID（升序）
     *
     * @param userId 用户 ID
     * @return 租户 ID 列表；无关系时返回空列表
     */
    List<Long> listEnabledTenantIds(Long userId);

    /**
     * 查询租户下的启用成员用户 ID（Wave 5.54 Phase 5.3：租户成员列表）
     *
     * @param tenantId 租户 ID
     * @return 启用关系的用户 ID 列表（升序）；无成员时返回空列表
     */
    List<Long> listEnabledUserIds(Long tenantId);

    /**
     * 判断用户与租户之间是否存在关系行（启用或停用均算存在）
     *
     * @param userId 用户 ID
     * @param tenantId 租户 ID
     * @return 存在返回 true，否则返回 false
     */
    boolean exists(Long userId, Long tenantId);

    /**
     * 幂等写入用户首页租户关系（存在但停用时重新置为启用）
     *
     * @param userId 用户 ID
     * @param tenantId 首页租户 ID
     */
    void ensureEnabled(Long userId, Long tenantId);

    /**
     * 软停用关系（置 {@code status=0}，保留关系行供审计与再次绑定）
     *
     * @param userId 用户 ID
     * @param tenantId 租户 ID
     */
    void disable(Long userId, Long tenantId);
}
