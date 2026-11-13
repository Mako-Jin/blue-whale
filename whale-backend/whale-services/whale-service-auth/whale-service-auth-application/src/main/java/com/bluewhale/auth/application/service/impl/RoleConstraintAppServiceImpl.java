package com.bluewhale.auth.application.service.impl;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.model.command.SaveRoleConstraintCommand;
import com.bluewhale.auth.application.model.query.ConstraintPageQuery;
import com.bluewhale.auth.application.model.vo.RoleConstraintVO;
import com.bluewhale.auth.application.port.CurrentUserProvider;
import com.bluewhale.auth.application.service.RoleConstraintAppService;
import com.bluewhale.auth.domain.aggregate.RoleConstraintAggregate;
import com.bluewhale.auth.domain.event.RoleConstraintDomainEvent;
import com.bluewhale.auth.domain.repository.RoleConstraintRepository;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色约束应用服务实现（权限中心扩展 M3）。
 *
 * <p>约束类型特有不变量（MUTEX ≥2 角色 / CARDINALITY max_assign &gt; 0）由聚合根强制；
 * 覆盖角色存在性在本层校验。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class RoleConstraintAppServiceImpl implements RoleConstraintAppService {

    private final RoleConstraintRepository constraintRepository;
    private final com.bluewhale.auth.domain.repository.RoleRepository roleRepository;
    private final CurrentUserProvider currentUserProvider;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 新建约束
     *
     * @param command 保存命令
     * @return 视图对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleConstraintVO create(SaveRoleConstraintCommand command) {
        Long tenantId = currentUserProvider.current().tokenTenantId();
        if (constraintRepository.existsByCode(tenantId, command.constraintCode(), null)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "约束编码在租户内已存在");
        }
        requireRolesExist(tenantId, command.roleIds());
        RoleConstraintAggregate constraint = RoleConstraintAggregate.create(tenantId,
                command.constraintCode(), command.constraintName(), command.constraintType(),
                command.roleIds(), command.maxAssign());
        if (command.status() != null && command.status() == RoleConstraintAggregate.STATUS_DISABLED) {
            constraint.changeStatus(RoleConstraintAggregate.STATUS_DISABLED);
        }
        RoleConstraintAggregate saved = constraintRepository.save(constraint);
        publish(saved, com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType.CREATED);
        return RoleConstraintVO.from(saved);
    }

    /**
     * 更新约束（编码 / 类型不可变）
     *
     * @param id 主键
     * @param command 保存命令
     * @return 视图对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleConstraintVO update(Long id, SaveRoleConstraintCommand command) {
        RoleConstraintAggregate constraint = require(id);
        constraint.rename(command.constraintName());
        requireRolesExist(constraint.getTenantId(), command.roleIds());
        constraint.changeRoles(command.roleIds());
        if (constraint.getConstraintType() == com.bluewhale.auth.domain.model.vo.ConstraintType.CARDINALITY
                && command.maxAssign() != null) {
            constraint.changeMaxAssign(command.maxAssign());
        }
        constraint.changeDescription(command.description());
        if (command.status() != null) {
            constraint.changeStatus(command.status() == RoleConstraintAggregate.STATUS_DISABLED
                    ? RoleConstraintAggregate.STATUS_DISABLED : RoleConstraintAggregate.STATUS_ENABLED);
        }
        RoleConstraintAggregate saved = constraintRepository.save(constraint);
        publish(saved, com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType.UPDATED);
        return RoleConstraintVO.from(saved);
    }

    /**
     * 删除约束
     *
     * @param id 主键
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        RoleConstraintAggregate constraint = require(id);
        constraintRepository.deleteById(id);
        publish(constraint, com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType.DELETED);
    }

    /**
     * 查询约束详情
     *
     * @param id 主键
     * @return 视图对象
     */
    @Override
    public RoleConstraintVO get(Long id) {
        return RoleConstraintVO.from(require(id));
    }

    /**
     * 分页查询约束
     *
     * @param query 分页查询
     * @return 分页结果
     */
    @Override
    public PageResult<RoleConstraintVO> page(ConstraintPageQuery query) {
        long current = Math.max(query.current(), 1L);
        long size = Math.max(query.size(), 1L);
        RoleConstraintRepository.ConstraintPage page = constraintRepository.page(current, size,
                query.keyword(), query.constraintType());
        List<RoleConstraintVO> records = page.records().stream().map(RoleConstraintVO::from).toList();
        return PageResult.of(records, page.total(), current, size);
    }

    /**
     * 按主键加载角色约束，不存在时抛业务异常。
     *
     * @param id 角色约束主键
     * @return 角色约束聚合根
     */
    private RoleConstraintAggregate require(Long id) {
        return constraintRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "角色约束不存在"));
    }

    /**
     * 校验约束引用的角色都属于当前租户；任一角色缺失即拒绝保存。
     *
     * @param tenantId 租户主键
     * @param roleIds 约束引用的角色主键集合，允许为空
     */
    private void requireRolesExist(Long tenantId, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        boolean anyMissing = roleIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .anyMatch(roleId -> roleRepository.findById(roleId)
                        .filter(r -> tenantId.equals(r.getTenantId()))
                        .isEmpty());
        if (anyMissing) {
            throw new BizException(ErrorCode.PARAM_ERROR, "约束引用的角色不存在");
        }
    }

    /**
     * 发布角色约束领域事件，供授权判定缓存失效与审计消费。
     *
     * @param constraint 角色约束聚合根
     * @param type 领域事件变更类型
     */
    private void publish(RoleConstraintAggregate constraint,
                         com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType type) {
        domainEventPublisher.publish(new RoleConstraintDomainEvent(constraint.getId(),
                constraint.getTenantId(), type, System.currentTimeMillis()));
    }
}
