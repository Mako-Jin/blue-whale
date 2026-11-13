package com.bluewhale.auth.application.service.impl;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.model.command.SaveUserGroupCommand;
import com.bluewhale.auth.application.model.query.UserGroupPageQuery;
import com.bluewhale.auth.application.model.vo.UserGroupVO;
import com.bluewhale.auth.application.port.CurrentUserProvider;
import com.bluewhale.auth.application.port.GroupMemberEvaluator;
import com.bluewhale.auth.application.service.UserGroupAppService;
import com.bluewhale.auth.domain.aggregate.UserGroupAggregate;
import com.bluewhale.auth.domain.event.UserGroupDomainEvent;
import com.bluewhale.auth.domain.model.vo.GroupFilters;
import com.bluewhale.auth.domain.repository.UserGroupRepository;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户组应用服务实现（权限中心扩展 M3）。
 *
 * <p>组树成环检测：更新父组时沿 {@code parentId} 链上溯，命中自身即拒绝；
 * 动态组成员重算经 {@link GroupMemberEvaluator} 求值后整体重建快照。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserGroupAppServiceImpl implements UserGroupAppService {

    /** 父链上溯防自环深度上限（防御脏数据导致的死循环） */
    private static final int MAX_PARENT_CHAIN_DEPTH = 50;

    private final UserGroupRepository userGroupRepository;
    private final GroupMemberEvaluator groupMemberEvaluator;
    private final CurrentUserProvider currentUserProvider;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 新建用户组
     *
     * @param command 保存命令
     * @return 视图对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserGroupVO create(SaveUserGroupCommand command) {
        Long tenantId = currentUserProvider.current().tokenTenantId();
        if (userGroupRepository.existsByCode(tenantId, command.groupCode(), null)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "组编码在租户内已存在");
        }
        UserGroupAggregate group = isDynamic(command.category())
                ? UserGroupAggregate.createDynamic(tenantId, command.groupCode(), command.groupName(),
                        command.parentId(), parseFilters(command.filtersJson()), command.description(),
                        command.sortNo())
                : UserGroupAggregate.createStatic(tenantId, command.groupCode(), command.groupName(),
                        command.parentId(), command.description(), command.sortNo());
        applyStatus(group, command.status());
        UserGroupAggregate saved = userGroupRepository.save(group);
        publish(saved, com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType.CREATED);
        return UserGroupVO.from(saved, command.filtersJson());
    }

    /**
     * 更新用户组（编码不可变；父组变更做链路成环检测）
     *
     * @param id 主键
     * @param command 保存命令
     * @return 视图对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserGroupVO update(Long id, SaveUserGroupCommand command) {
        UserGroupAggregate group = require(id);
        group.rename(command.groupName());
        ensureNoCycle(group, command.parentId());
        group.changeParent(command.parentId());
        group.changeDescription(command.description());
        group.changeSortNo(command.sortNo() == null ? 0 : command.sortNo());
        if (group.isDynamic()) {
            group.changeFilters(parseFilters(command.filtersJson()));
        }
        applyStatus(group, command.status());
        UserGroupAggregate saved = userGroupRepository.save(group);
        publish(saved, com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType.UPDATED);
        return UserGroupVO.from(saved, command.filtersJson());
    }

    /**
     * 删除用户组（并清理成员关系）
     *
     * @param id 主键
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        UserGroupAggregate group = require(id);
        userGroupRepository.deleteById(id);
        publish(group, com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType.DELETED);
    }

    /**
     * 查询用户组详情
     *
     * @param id 主键
     * @return 视图对象
     */
    @Override
    public UserGroupVO get(Long id) {
        UserGroupAggregate group = require(id);
        return UserGroupVO.from(group, group.getFilters() == null ? null : serializeFilters(group.getFilters()));
    }

    /**
     * 分页查询用户组
     *
     * @param query 分页查询
     * @return 分页结果
     */
    @Override
    public PageResult<UserGroupVO> page(UserGroupPageQuery query) {
        long current = Math.max(query.current(), 1L);
        long size = Math.max(query.size(), 1L);
        UserGroupRepository.GroupPage page = userGroupRepository.page(current, size, query.keyword(),
                query.category());
        List<UserGroupVO> records = page.records().stream()
                .map(g -> UserGroupVO.from(g,
                        g.getFilters() == null ? null : serializeFilters(g.getFilters())))
                .toList();
        return PageResult.of(records, page.total(), current, size);
    }

    /**
     * 全量替换静态组成员
     *
     * @param id 组 ID
     * @param memberUserIds 成员用户 ID 列表（空列表表示清空）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceMembers(Long id, List<Long> memberUserIds) {
        UserGroupAggregate group = require(id);
        List<Long> distinct = group.normalizeMembers(memberUserIds);
        userGroupRepository.replaceMembers(id, distinct);
        publish(group, com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType.UPDATED);
    }

    /**
     * 重算动态组成员（按 filters 规则求值并重建快照）
     *
     * @param id 组 ID
     * @return 命中成员数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public long recomputeDynamicMembers(Long id) {
        UserGroupAggregate group = require(id);
        if (!group.isDynamic()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "静态组成员请走显式维护接口");
        }
        List<Long> memberIds = groupMemberEvaluator.evaluate(group.getTenantId(), group.getFilters());
        userGroupRepository.replaceDynamicMembers(id, memberIds);
        publish(group, com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType.UPDATED);
        return memberIds.size();
    }

    /**
     * 查询组成员用户 ID 列表
     *
     * @param id 组 ID
     * @return 成员用户 ID 列表
     */
    @Override
    public List<Long> listMemberUserIds(Long id) {
        require(id);
        return userGroupRepository.listMemberUserIds(id);
    }

    /**
     * 沿父链上溯检测成环：目标父链中出现自身即拒绝
     *
     * @param group 当前组
     * @param newParentId 目标父组 ID
     */
    private void ensureNoCycle(UserGroupAggregate group, Long newParentId) {
        Long target = newParentId == null ? 0L : newParentId;
        if (target == 0L || group.getId() == null || !target.equals(group.getId())) {
            Set<Long> visited = new HashSet<>();
            Long cursor = target;
            for (int depth = 0; cursor != null && cursor != 0L && depth < MAX_PARENT_CHAIN_DEPTH; depth++) {
                if (cursor.equals(group.getId())) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "组树不得成环：目标父链包含自身");
                }
                if (!visited.add(cursor)) {
                    log.warn("组树父链存在既有环，终止上溯: groupId={}, cursor={}", group.getId(), cursor);
                    break;
                }
                cursor = userGroupRepository.findById(cursor).map(UserGroupAggregate::getParentId).orElse(0L);
            }
        }
    }

    private UserGroupAggregate require(Long id) {
        return userGroupRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "用户组不存在"));
    }

    private void applyStatus(UserGroupAggregate group, Integer status) {
        if (status != null && status == UserGroupAggregate.STATUS_DISABLED) {
            group.changeStatus(UserGroupAggregate.STATUS_DISABLED);
        }
    }

    private boolean isDynamic(String category) {
        return UserGroupAggregate.CATEGORY_DYNAMIC.equals(category);
    }

    private GroupFilters parseFilters(String filtersJson) {
        return userGroupRepository.parseFilters(filtersJson);
    }

    private String serializeFilters(GroupFilters filters) {
        return userGroupRepository.serializeFilters(filters);
    }

    private void publish(UserGroupAggregate group, com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType type) {
        domainEventPublisher.publish(new UserGroupDomainEvent(group.getId(), group.getTenantId(), type,
                System.currentTimeMillis()));
    }
}
