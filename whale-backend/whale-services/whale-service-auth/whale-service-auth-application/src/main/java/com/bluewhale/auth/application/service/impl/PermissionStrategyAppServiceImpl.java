package com.bluewhale.auth.application.service.impl;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.model.command.SavePermissionStrategyCommand;
import com.bluewhale.auth.application.model.query.StrategyPageQuery;
import com.bluewhale.auth.application.model.vo.PermissionStrategyVO;
import com.bluewhale.auth.application.port.CurrentUserProvider;
import com.bluewhale.auth.application.service.PermissionStrategyAppService;
import com.bluewhale.auth.domain.aggregate.PermissionStrategyAggregate;
import com.bluewhale.auth.domain.event.PermissionStrategyDomainEvent;
import com.bluewhale.auth.domain.model.vo.StrategyCondition;
import com.bluewhale.auth.domain.repository.PermissionStrategyRepository;
import com.bluewhale.auth.domain.repository.RoleRepository;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 权限策略应用服务实现（权限中心扩展 M3）。
 *
 * <p>不变量编排：role_ids 引用校验（仓储查角色存在性）；同租户仅一条默认策略——
 * 标记默认时先 {@code clearDefault} 再保存；资源引用校验由 {@code MenuRepository}
 * 存在性查询承担（无独立资源表，资源即菜单/权限条目）。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class PermissionStrategyAppServiceImpl implements PermissionStrategyAppService {

    private final PermissionStrategyRepository strategyRepository;
    private final RoleRepository roleRepository;
    private final CurrentUserProvider currentUserProvider;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 新建策略
     *
     * @param command 保存命令
     * @return 视图对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PermissionStrategyVO create(SavePermissionStrategyCommand command) {
        Long tenantId = currentUserProvider.current().tokenTenantId();
        if (strategyRepository.existsByCode(tenantId, command.strategyCode(), null)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "策略编码在租户内已存在");
        }
        requireRolesExist(tenantId, command.roleIds());
        PermissionStrategyAggregate strategy = PermissionStrategyAggregate.create(tenantId,
                command.strategyCode(), command.strategyName(), command.strategyType(),
                command.roleIds(), command.resourceIds(), parseConditions(command.conditionJson()),
                Boolean.TRUE.equals(command.isDefault()), command.priority());
        applyStatus(strategy, command.status());
        return saveAndClearDefault(strategy, command.conditionJson(),
                com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType.CREATED);
    }

    /**
     * 更新策略（编码 / 类型不可变）
     *
     * @param id 主键
     * @param command 保存命令
     * @return 视图对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PermissionStrategyVO update(Long id, SavePermissionStrategyCommand command) {
        PermissionStrategyAggregate strategy = require(id);
        strategy.rename(command.strategyName());
        requireRolesExist(strategy.getTenantId(), command.roleIds());
        strategy.changeTargets(command.roleIds(), command.resourceIds());
        strategy.changeConditions(parseConditions(command.conditionJson()));
        if (command.priority() != null) {
            strategy.changePriority(command.priority());
        }
        strategy.changeDescription(command.description());
        applyStatus(strategy, command.status());
        if (Boolean.TRUE.equals(command.isDefault())) {
            strategy.markDefault();
        } else if (Boolean.FALSE.equals(command.isDefault())) {
            strategy.unmarkDefault();
        }
        return saveAndClearDefault(strategy, command.conditionJson(),
                com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType.UPDATED);
    }

    /**
     * 删除策略
     *
     * @param id 主键
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PermissionStrategyAggregate strategy = require(id);
        strategyRepository.deleteById(id);
        publish(strategy, com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType.DELETED);
    }

    /**
     * 查询策略详情
     *
     * @param id 主键
     * @return 视图对象
     */
    @Override
    public PermissionStrategyVO get(Long id) {
        PermissionStrategyAggregate strategy = require(id);
        return PermissionStrategyVO.from(strategy, serializeConditions(strategy));
    }

    /**
     * 分页查询策略
     *
     * @param query 分页查询
     * @return 分页结果
     */
    @Override
    public PageResult<PermissionStrategyVO> page(StrategyPageQuery query) {
        long current = Math.max(query.current(), 1L);
        long size = Math.max(query.size(), 1L);
        PermissionStrategyRepository.StrategyPage page = strategyRepository.page(current, size,
                query.keyword(), query.strategyType());
        List<PermissionStrategyVO> records = page.records().stream()
                .map(s -> PermissionStrategyVO.from(s, serializeConditions(s)))
                .toList();
        return PageResult.of(records, page.total(), current, size);
    }

    /**
     * 设置为默认策略（同租户仅一条，其余自动取消）
     *
     * @param id 主键
     * @return 视图对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PermissionStrategyVO setDefault(Long id) {
        PermissionStrategyAggregate strategy = require(id);
        strategy.markDefault();
        return saveAndClearDefault(strategy, serializeConditions(strategy),
                com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType.UPDATED);
    }

    private PermissionStrategyVO saveAndClearDefault(PermissionStrategyAggregate strategy,
                                                     String conditionJson,
                                                     com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType type) {
        if (strategy.isDefaultStrategy()) {
            strategyRepository.clearDefault(strategy.getTenantId(), strategy.getId());
        }
        PermissionStrategyAggregate saved = strategyRepository.save(strategy);
        publish(saved, type);
        return PermissionStrategyVO.from(saved, conditionJson);
    }

    private PermissionStrategyAggregate require(Long id) {
        return strategyRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "权限策略不存在"));
    }

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
            throw new BizException(ErrorCode.PARAM_ERROR, "策略引用的角色不存在");
        }
    }

    private void applyStatus(PermissionStrategyAggregate strategy, Integer status) {
        if (status != null && status == PermissionStrategyAggregate.STATUS_DISABLED) {
            strategy.changeStatus(PermissionStrategyAggregate.STATUS_DISABLED);
        }
    }

    private List<StrategyCondition> parseConditions(String conditionJson) {
        return strategyRepository.parseConditions(conditionJson);
    }

    private String serializeConditions(PermissionStrategyAggregate strategy) {
        return strategyRepository.serializeConditions(strategy.getConditions());
    }

    private void publish(PermissionStrategyAggregate strategy,
                         com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType type) {
        domainEventPublisher.publish(new PermissionStrategyDomainEvent(strategy.getId(),
                strategy.getTenantId(), type, System.currentTimeMillis()));
    }
}
