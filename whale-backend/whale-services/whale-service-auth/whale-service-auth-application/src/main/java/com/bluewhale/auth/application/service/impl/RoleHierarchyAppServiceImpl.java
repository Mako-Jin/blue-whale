package com.bluewhale.auth.application.service.impl;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.model.vo.RoleVO;
import com.bluewhale.auth.application.service.RoleHierarchyAppService;
import com.bluewhale.auth.domain.aggregate.RoleAggregate;
import com.bluewhale.auth.domain.event.AuthDomainEvent;
import com.bluewhale.auth.domain.event.RoleHierarchyDomainEvent;
import com.bluewhale.auth.domain.repository.RoleRepository;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * 角色继承应用服务实现（权限中心扩展 M3）。
 *
 * <p>成环检测：挂载父角色时从目标父角色沿 {@code parentId} 上溯，命中自身即拒绝；
 * 层级只能由父角色 +1（聚合行为保证）；父角色存在性 / 同租户校验在本层。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleHierarchyAppServiceImpl implements RoleHierarchyAppService {

    /** 父链上溯防自环深度上限（防御脏数据导致的死循环） */
    private static final int MAX_PARENT_CHAIN_DEPTH = 50;

    private final RoleRepository roleRepository;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 挂载到父角色
     *
     * @param roleId 角色 ID
     * @param parentRoleId 父角色 ID
     * @return 更新后的角色视图
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleVO attachParent(Long roleId, Long parentRoleId) {
        RoleAggregate role = require(roleId);
        RoleAggregate parent = require(parentRoleId);
        if (!role.getTenantId().equals(parent.getTenantId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "父角色必须同租户");
        }
        ensureNoCycle(role, parentRoleId);
        role.attachParent(parent);
        RoleAggregate saved = roleRepository.save(role);
        publish(saved);
        return RoleVO.from(saved);
    }

    /**
     * 解除父角色
     *
     * @param roleId 角色 ID
     * @return 更新后的角色视图
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleVO detachParent(Long roleId) {
        RoleAggregate role = require(roleId);
        role.detachParent();
        RoleAggregate saved = roleRepository.save(role);
        publish(saved);
        return RoleVO.from(saved);
    }

    /**
     * 变更继承策略
     *
     * @param roleId 角色 ID
     * @param strategy 继承策略字符串
     * @return 更新后的角色视图
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleVO changeInheritStrategy(Long roleId, String strategy) {
        RoleAggregate role = require(roleId);
        role.changeInheritStrategy(strategy);
        RoleAggregate saved = roleRepository.save(role);
        publish(saved);
        return RoleVO.from(saved);
    }

    /**
     * 变更角色分类
     *
     * @param roleId 角色 ID
     * @param category 分类
     * @return 更新后的角色视图
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleVO changeCategory(Long roleId, String category) {
        RoleAggregate role = require(roleId);
        role.changeCategory(category);
        RoleAggregate saved = roleRepository.save(role);
        publish(saved);
        return RoleVO.from(saved);
    }

    /**
     * 从目标父角色沿父链上溯检测成环
     *
     * @param role 当前角色
     * @param parentRoleId 目标父角色 ID
     */
    private void ensureNoCycle(RoleAggregate role, Long parentRoleId) {
        Set<Long> visited = new HashSet<>();
        Long cursor = parentRoleId;
        for (int depth = 0; cursor != null && cursor != 0L && depth < MAX_PARENT_CHAIN_DEPTH; depth++) {
            if (cursor.equals(role.getId())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "角色继承链不得成环：目标父链包含自身");
            }
            if (!visited.add(cursor)) {
                log.warn("角色父链存在既有环，终止上溯: roleId={}, cursor={}", role.getId(), cursor);
                break;
            }
            cursor = roleRepository.findById(cursor).map(RoleAggregate::getParentId).orElse(0L);
        }
    }

    private RoleAggregate require(Long roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "角色不存在"));
    }

    private void publish(RoleAggregate role) {
        domainEventPublisher.publish(new RoleHierarchyDomainEvent(role.getId(), role.getTenantId(),
                AuthDomainEvent.ChangeType.UPDATED, System.currentTimeMillis()));
    }
}
