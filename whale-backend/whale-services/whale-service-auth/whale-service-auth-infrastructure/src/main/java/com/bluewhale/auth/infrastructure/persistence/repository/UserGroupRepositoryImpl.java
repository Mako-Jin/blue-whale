package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.auth.application.port.GroupMemberEvaluator;
import com.bluewhale.auth.domain.aggregate.UserGroupAggregate;
import com.bluewhale.auth.domain.model.vo.GroupFilters;
import com.bluewhale.auth.domain.repository.UserGroupRepository;
import com.bluewhale.auth.infrastructure.persistence.assembler.UserGroupPersistenceAssembler;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysUserGroupMapper;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysUserGroupMemberMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserGroupMemberPo;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserGroupPo;
import com.bluewhale.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 用户组仓储 MyBatis-Plus 实现（权限中心扩展 M3）。
 *
 * <p>组表由 {@code deleted} 列承担逻辑删除（MyBatis-Plus 全局配置）；成员表有
 * {@code uk_group_user} 唯一键，整体替换采用「按 group_id 物理删后重插」（自定义 SQL
 * 绕过逻辑删除与租户行级插件）。{@code listGroupIdsByUser} 的动态组求值委托
 * {@link GroupMemberEvaluator} 应用端口适配器。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class UserGroupRepositoryImpl implements UserGroupRepository {

    private final SysUserGroupMapper groupMapper;
    private final SysUserGroupMemberMapper memberMapper;
    private final GroupMemberEvaluator groupMemberEvaluator;

    /**
     * 保存用户组聚合：无主键新增（回填 ID），有主键按 ID 更新
     *
     * @param group 用户组聚合
     * @return 回填主键后的聚合
     */
    @Override
    public UserGroupAggregate save(UserGroupAggregate group) {
        SysUserGroupPo po = UserGroupPersistenceAssembler.toPo(group);
        if (po.getId() == null) {
            groupMapper.insert(po);
        } else {
            groupMapper.updateById(po);
        }
        return UserGroupPersistenceAssembler.toDomain(po);
    }

    /**
     * 按主键查询用户组（当前租户内查询）
     *
     * @param id 主键
     * @return 用户组聚合；不存在时返回空
     */
    @Override
    public Optional<UserGroupAggregate> findById(Long id) {
        SysUserGroupPo po = groupMapper.selectOne(new LambdaQueryWrapper<SysUserGroupPo>()
                .eq(SysUserGroupPo::getId, id)
                .eq(SysUserGroupPo::getTenantId, TenantContext.requireId()));
        return Optional.ofNullable(po).map(UserGroupPersistenceAssembler::toDomain);
    }

    /**
     * 判断组编码在租户内是否已存在（排除指定组自身）
     *
     * @param tenantId 租户 ID
     * @param groupCode 组编码
     * @param excludeGroupId 排除的组 ID（新建传 {@code null}）
     * @return 已存在返回 true
     */
    @Override
    public boolean existsByCode(Long tenantId, String groupCode, Long excludeGroupId) {
        LambdaQueryWrapper<SysUserGroupPo> wrapper = new LambdaQueryWrapper<SysUserGroupPo>()
                .eq(SysUserGroupPo::getTenantId, tenantId)
                .eq(SysUserGroupPo::getGroupCode, groupCode);
        if (excludeGroupId != null) {
            wrapper.ne(SysUserGroupPo::getId, excludeGroupId);
        }
        Long count = groupMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    /**
     * 分页查询用户组（keyword 模糊匹配编码/名称，按 sortNo 升序）
     *
     * @param current 当前页码
     * @param size 每页条数
     * @param keyword 关键字（可空）
     * @param category 类别过滤（可空）
     * @return 用户组分页结果
     */
    @Override
    public GroupPage page(long current, long size, String keyword, String category) {
        LambdaQueryWrapper<SysUserGroupPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserGroupPo::getTenantId, TenantContext.requireId());
        if (StringUtils.hasText(keyword)) {
            String escaped = escapeLike(keyword);
            wrapper.and(w -> w.like(SysUserGroupPo::getGroupCode, escaped)
                    .or().like(SysUserGroupPo::getGroupName, escaped));
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(SysUserGroupPo::getCategory, category);
        }
        wrapper.orderByAsc(SysUserGroupPo::getSortNo).orderByDesc(SysUserGroupPo::getCreateTime);
        Page<SysUserGroupPo> page = groupMapper.selectPage(Page.of(current, size), wrapper);
        List<UserGroupAggregate> records = page.getRecords().stream()
                .map(UserGroupPersistenceAssembler::toDomain).toList();
        return new GroupPage(records, page.getTotal());
    }

    /**
     * 删除用户组：组逻辑删除，成员关系物理清理
     *
     * @param id 组 ID
     */
    @Override
    public void deleteById(Long id) {
        groupMapper.delete(new LambdaQueryWrapper<SysUserGroupPo>()
                .eq(SysUserGroupPo::getId, id)
                .eq(SysUserGroupPo::getTenantId, TenantContext.requireId()));
        memberMapper.deleteByGroupId(id);
    }

    /**
     * 全量覆盖静态组成员（先按 group_id 物理删后重插，成员来源 STATIC）
     *
     * <p>先删后插要求调用方已开启事务（MANDATORY 强制）。</p>
     *
     * @param groupId 组 ID
     * @param memberUserIds 成员用户 ID 列表
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void replaceMembers(Long groupId, List<Long> memberUserIds) {
        replaceMembersInternal(groupId, memberUserIds, "STATIC");
    }

    /**
     * 用规则求值结果全量重建动态组成员快照（成员来源 DYNAMIC）
     *
     * <p>先删后插要求调用方已开启事务（MANDATORY 强制）。</p>
     *
     * @param groupId 组 ID
     * @param memberUserIds 求值出的成员用户 ID 列表
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void replaceDynamicMembers(Long groupId, List<Long> memberUserIds) {
        replaceMembersInternal(groupId, memberUserIds, "DYNAMIC");
    }

    /**
     * 查询组成员用户 ID 列表
     *
     * @param groupId 组 ID
     * @return 成员用户 ID 列表；无成员时返回空列表
     */
    @Override
    public List<Long> listMemberUserIds(Long groupId) {
        return memberMapper.selectList(new LambdaQueryWrapper<SysUserGroupMemberPo>()
                        .eq(SysUserGroupMemberPo::getGroupId, groupId))
                .stream().map(SysUserGroupMemberPo::getUserId).toList();
    }

    /**
     * 查询租户内全部启用状态的用户组
     *
     * @param tenantId 租户 ID
     * @return 启用用户组列表
     */
    @Override
    public List<UserGroupAggregate> listEnabledByTenant(Long tenantId) {
        return groupMapper.selectList(new LambdaQueryWrapper<SysUserGroupPo>()
                        .eq(SysUserGroupPo::getTenantId, tenantId)
                        .eq(SysUserGroupPo::getStatus, UserGroupAggregate.STATUS_ENABLED)
                        .orderByAsc(SysUserGroupPo::getSortNo))
                .stream().map(UserGroupPersistenceAssembler::toDomain).toList();
    }

    /**
     * 查询用户所属的全部启用组 ID：静态成员关系与动态组求值结果合并去重
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @return 组 ID 列表；无归属时返回空列表
     */
    @Override
    public List<Long> listGroupIdsByUser(Long tenantId, Long userId) {
        List<UserGroupAggregate> enabledGroups = listEnabledByTenant(tenantId);
        if (enabledGroups.isEmpty()) {
            return List.of();
        }
        Set<Long> staticGroupIds = new HashSet<>(memberMapper
                .selectList(new LambdaQueryWrapper<SysUserGroupMemberPo>()
                        .eq(SysUserGroupMemberPo::getTenantId, tenantId)
                        .eq(SysUserGroupMemberPo::getUserId, userId))
                .stream().map(SysUserGroupMemberPo::getGroupId).toList());
        List<Long> result = new ArrayList<>();
        for (UserGroupAggregate group : enabledGroups) {
            if (group.isDynamic()) {
                if (groupMemberEvaluator.evaluate(tenantId, group.getFilters()).contains(userId)) {
                    result.add(group.getId());
                }
            } else if (staticGroupIds.contains(group.getId())) {
                result.add(group.getId());
            }
        }
        return result.stream().distinct().toList();
    }

    /**
     * 解析动态组过滤规则（JSON 反序列化为值对象；存储为空时返回空规则）
     *
     * @param filtersJson 过滤规则 JSON（可空）
     * @return 过滤规则值对象
     */
    @Override
    public GroupFilters parseFilters(String filtersJson) {
        return UserGroupPersistenceAssembler.parseFilters(filtersJson);
    }

    /**
     * 序列化过滤规则值对象为 JSON（应用层 VO 透传用）
     *
     * @param filters 过滤规则值对象
     * @return JSON 字符串；空规则返回 {@code null}
     */
    @Override
    public String serializeFilters(GroupFilters filters) {
        return UserGroupPersistenceAssembler.serializeFilters(filters);
    }

    /**
     * 成员关系整体替换内部实现（先按 group_id 物理删，再逐条重插）
     *
     * @param groupId 组 ID
     * @param memberUserIds 成员用户 ID 列表（允许空列表表示清空）
     * @param memberSource 成员来源（STATIC / DYNAMIC）
     */
    private void replaceMembersInternal(Long groupId, List<Long> memberUserIds, String memberSource) {
        memberMapper.deleteByGroupId(groupId);
        List<Long> distinct = memberUserIds == null ? List.of()
                : memberUserIds.stream().filter(Objects::nonNull).distinct().toList();
        SysUserGroupPo group = groupMapper.selectById(groupId);
        Long tenantId = group != null && group.getTenantId() != null
                ? group.getTenantId() : TenantContext.requireId();
        for (Long userId : distinct) {
            memberMapper.insert(UserGroupPersistenceAssembler
                    .toMemberPo(tenantId, groupId, userId, memberSource));
        }
    }

    /**
     * LIKE 关键字转义：反斜杠、百分号、下划线（MySQL LIKE 默认以反斜杠为转义符）
     *
     * @param keyword 原始关键字
     * @return 转义后的关键字
     */
    private static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
