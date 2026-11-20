package com.bluewhale.auth.application.service.impl;

import com.bluewhale.auth.application.port.RoleConstraintChecker;
import com.bluewhale.auth.domain.aggregate.RoleConstraintAggregate;
import com.bluewhale.auth.domain.model.vo.ConstraintType;
import com.bluewhale.auth.domain.repository.RoleConstraintRepository;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 角色约束校验实现（权限中心扩展 M3）。
 *
 * <p>纯领域仓储依赖，无框架出站能力；分配角色（整体替换口径）前按租户加载全部
 * 启用约束逐一校验。MUTEX：目标集合命中同一互斥集 ≥2 个即拒绝；CARDINALITY：
 * 目标集合命中同一基数集超过 {@code maxAssign} 即拒绝。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class RoleConstraintCheckerImpl implements RoleConstraintChecker {

    private final RoleConstraintRepository constraintRepository;

    /**
     * 校验目标角色集合是否满足全部启用约束
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param roleIds 目标角色 ID 集合（允许空集合）
     * @throws BizException 违反任一约束时抛出（携带约束名称）
     */
    @Override
    public void checkAssignRoles(Long tenantId, Long userId, Collection<Long> roleIds) {
        if (tenantId == null || roleIds == null || roleIds.isEmpty()) {
            return;
        }
        Set<Long> targetSet = new HashSet<>(roleIds);
        if (targetSet.isEmpty()) {
            return;
        }
        List<RoleConstraintAggregate> constraints = constraintRepository.listEnabledByTenant(tenantId);
        for (RoleConstraintAggregate constraint : constraints) {
            long overlap = constraint.getRoleIds().stream().filter(targetSet::contains).count();
            if (constraint.getConstraintType() == ConstraintType.MUTEX && overlap >= 2) {
                throw new BizException(ErrorCode.PARAM_ERROR, "auth.role.mutex-violated",
                        "违反角色互斥约束「" + constraint.getConstraintName() + "」："
                                + "用户 " + userId + " 不能同时持有这些角色",
                        constraint.getConstraintName(), userId);
            }
            if (constraint.getConstraintType() == ConstraintType.CARDINALITY
                    && constraint.getMaxAssign() != null && overlap > constraint.getMaxAssign()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "auth.role.cardinality-violated",
                        "违反角色基数约束「" + constraint.getConstraintName() + "」："
                                + "最多同时持有 " + constraint.getMaxAssign() + " 个相关角色",
                        constraint.getConstraintName(), constraint.getMaxAssign());
            }
        }
    }
}
