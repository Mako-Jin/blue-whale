package com.bluewhale.auth.application.port;

import java.util.Collection;

/**
 * 角色约束校验端口（权限中心扩展 M3）。
 *
 * <p>分配角色（整体替换口径）前校验租户内全部启用约束：
 * {@code MUTEX} 互斥——目标角色集合命中同一互斥集 ≥2 个即拒绝；
 * {@code CARDINALITY} 基数——目标角色集合命中同一基数集超过 {@code maxAssign} 即拒绝。
 * 违约时抛业务异常并携带约束名称。</p>
 *
 * @author BlueWhale
 */
public interface RoleConstraintChecker {

    /**
     * 校验目标角色集合是否满足全部启用约束（整体替换口径：以目标集合为最终持有集合）
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID（用于异常定位与审计）
     * @param roleIds 目标角色 ID 集合（允许空集合，表示清空角色）
     * @throws com.bluewhale.common.core.exception.BizException 违反任一约束时抛出
     */
    void checkAssignRoles(Long tenantId, Long userId, Collection<Long> roleIds);
}
