package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.auth.domain.aggregate.UserAggregate;
import com.bluewhale.auth.domain.model.entity.UserRoleAssignment;
import com.bluewhale.auth.domain.repository.UserRepository;
import com.bluewhale.auth.infrastructure.persistence.po.SysRolePo;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserPo;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserRolePo;
import com.bluewhale.auth.infrastructure.persistence.assembler.UserPersistenceAssembler;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysRoleMapper;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysUserMapper;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysUserRoleMapper;
import com.bluewhale.auth.infrastructure.persistence.TenantContextSupport;
import com.bluewhale.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@link UserRepository} 的 MyBatis-Plus 实现（REQ-ARCH-001 / T-DDD-8）：以既有 Mapper 完成
 * PO 读写，并借 {@link UserPersistenceAssembler} 完成 PO ↔ 领域对象转换。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    /** 状态：启用 */
    private static final int STATUS_ENABLED = 1;

    /** 逻辑删除：未删除 */
    private static final int DELETED_NO = 0;

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;

    @Override
    public UserAggregate save(UserAggregate user) {
        SysUserPo po = UserPersistenceAssembler.toPo(user);
        if (po.getId() == null) {
            userMapper.insert(po);
        } else if (userMapper.updateById(po) == 0) {
            // 领域对象来自本事务内的 selectOne（已过滤逻辑删除），影响 0 行说明记录已被并发删除
            throw new IllegalStateException("更新用户失败，记录不存在或已被删除: id=" + po.getId());
        }
        return UserPersistenceAssembler.toDomain(po);
    }

    @Override
    public Optional<UserAggregate> findById(Long id) {
        SysUserPo po = userMapper.selectOne(new LambdaQueryWrapper<SysUserPo>()
                .eq(SysUserPo::getId, id)
                .eq(SysUserPo::getTenantId, TenantContext.requireId()));
        return Optional.ofNullable(po).map(UserPersistenceAssembler::toDomain);
    }

    @Override
    public Optional<UserAggregate> findByIdAcrossTenants(Long id) {
        // sys_user 归属首页租户，令牌租户可能是切换后的其他租户：
        // 临时清空租户上下文以绕过行级过滤，读取后立即恢复原值（含 null）
        return TenantContextSupport.runWithoutTenant(() -> {
            SysUserPo po = userMapper.selectById(id);
            return Optional.ofNullable(po).map(UserPersistenceAssembler::toDomain);
        });
    }

    @Override
    public List<UserAggregate> findByIdsAcrossTenants(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        // 跨租户批量加载（语义同 findByIdAcrossTenants）：临时清空租户上下文绕过行级过滤
        return TenantContextSupport.runWithoutTenant(() ->
                userMapper.selectList(new LambdaQueryWrapper<SysUserPo>()
                                .in(SysUserPo::getId, ids))
                        .stream().map(UserPersistenceAssembler::toDomain).toList());
    }

    @Override
    public Map<Long, List<Long>> listRoleIdsByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        // 仅返回当前租户内的角色关联（跨租户角色不参与本租户的角色管理展示）；
        // 先查本租户角色 ID，再以参数化 in() 过滤，不使用 inSql 字符串拼接
        List<Long> tenantRoleIds = listTenantRoleIds(TenantContext.requireId());
        if (tenantRoleIds.isEmpty()) {
            return Map.of();
        }
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRolePo>()
                        .in(SysUserRolePo::getUserId, userIds)
                        .in(SysUserRolePo::getRoleId, tenantRoleIds))
                .stream()
                .collect(Collectors.groupingBy(SysUserRolePo::getUserId,
                        Collectors.mapping(SysUserRolePo::getRoleId, Collectors.toList())));
    }

    @Override
    public Optional<UserAggregate> findByUsername(String username) {
        SysUserPo po = userMapper.selectOne(new LambdaQueryWrapper<SysUserPo>()
                .eq(SysUserPo::getTenantId, TenantContext.requireId())
                .eq(SysUserPo::getUsername, username)
                .last("limit 1"));
        return Optional.ofNullable(po).map(UserPersistenceAssembler::toDomain);
    }

    @Override
    public Optional<UserAggregate> findByTenantIdAndUsername(Long tenantId, String username) {
        SysUserPo po = userMapper.selectOne(new LambdaQueryWrapper<SysUserPo>()
                .eq(SysUserPo::getTenantId, tenantId)
                .eq(SysUserPo::getUsername, username)
                .last("limit 1"));
        return Optional.ofNullable(po).map(UserPersistenceAssembler::toDomain);
    }

    @Override
    public boolean existsByUsername(Long tenantId, String username) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUserPo>()
                .eq(SysUserPo::getTenantId, tenantId)
                .eq(SysUserPo::getUsername, username));
        return count != null && count > 0;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void replaceRoles(Long userId, List<Long> roleIds) {
        // 一人多租户：只清理当前租户角色的关联行，避免误删该用户在其他租户的角色；
        // 复用参数化 SQL（不 inSql 拼接）。先删后插要求调用方已开启事务（MANDATORY 强制）
        userRoleMapper.deleteByUserAndRoleTenant(userId, TenantContext.requireId());
        for (Long roleId : roleIds) {
            userRoleMapper.insert(UserPersistenceAssembler.toAssignmentPo(UserRoleAssignment.of(userId, roleId)));
        }
    }

    @Override
    public List<Long> listRoleIds(Long userId) {
        // 仅返回当前租户内的角色关联（跨租户角色不参与本租户的角色管理展示）；
        // 先查本租户角色 ID，再以参数化 in() 过滤，不使用 inSql 字符串拼接
        List<Long> tenantRoleIds = listTenantRoleIds(TenantContext.requireId());
        if (tenantRoleIds.isEmpty()) {
            return List.of();
        }
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRolePo>()
                        .eq(SysUserRolePo::getUserId, userId)
                        .in(SysUserRolePo::getRoleId, tenantRoleIds))
                .stream().map(SysUserRolePo::getRoleId).toList();
    }

    @Override
    public List<Long> listUserIdsByRoleId(Long roleId) {
        if (roleId == null) {
            return List.of();
        }
        // sys_user_role 无租户维度且在租户插件忽略白名单内，按角色全局反查（缓存失效链路专用）
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRolePo>()
                        .eq(SysUserRolePo::getRoleId, roleId))
                .stream().map(SysUserRolePo::getUserId).distinct().toList();
    }

    @Override
    public void removeRolesInTenant(Long userId, Long tenantId) {
        if (userId == null || tenantId == null) {
            return;
        }
        // 只清理目标租户的角色关联，避免误删该用户在其他租户的角色
        userRoleMapper.deleteByUserAndRoleTenant(userId, tenantId);
    }

    @Override
    public UserPage page(long current, long size, String keyword) {
        LambdaQueryWrapper<SysUserPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserPo::getTenantId, TenantContext.requireId());
        if (StringUtils.hasText(keyword)) {
            String escaped = escapeLike(keyword);
            wrapper.and(w -> w.like(SysUserPo::getUsername, escaped)
                    .or().like(SysUserPo::getNickname, escaped)
                    .or().like(SysUserPo::getPhone, escaped));
        }
        wrapper.orderByDesc(SysUserPo::getCreateTime);
        Page<SysUserPo> page = userMapper.selectPage(Page.of(current, size), wrapper);
        List<UserAggregate> records = page.getRecords().stream().map(UserPersistenceAssembler::toDomain).toList();
        return new UserPage(records, page.getTotal());
    }

    @Override
    public UserPage directoryPage(long current, long size, Collection<Long> deptIds, String keyword) {
        LambdaQueryWrapper<SysUserPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserPo::getTenantId, TenantContext.requireId());
        wrapper.eq(SysUserPo::getStatus, STATUS_ENABLED);
        wrapper.eq(SysUserPo::getDeleted, DELETED_NO);
        if (deptIds != null) {
            // 空集合走 in 会生成 `in ()` 的非法 SQL，直接给一个恒假条件
            if (deptIds.isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in(SysUserPo::getDeptId, deptIds);
            }
        }
        if (StringUtils.hasText(keyword)) {
            String escaped = escapeLike(keyword);
            wrapper.and(w -> w.like(SysUserPo::getUsername, escaped)
                    .or().like(SysUserPo::getNickname, escaped)
                    .or().like(SysUserPo::getPhone, escaped));
        }
        wrapper.orderByAsc(SysUserPo::getId);
        Page<SysUserPo> page = userMapper.selectPage(Page.of(current, size), wrapper);
        List<UserAggregate> records = page.getRecords().stream().map(UserPersistenceAssembler::toDomain).toList();
        return new UserPage(records, page.getTotal());
    }

    @Override
    public List<UserAggregate> findByRoleCode(String roleCode, int limit) {
        if (!StringUtils.hasText(roleCode)) {
            return List.of();
        }
        Long tenantId = TenantContext.requireId();
        SysRolePo role = roleMapper.selectOne(new LambdaQueryWrapper<SysRolePo>()
                .eq(SysRolePo::getTenantId, tenantId)
                .eq(SysRolePo::getRoleCode, roleCode)
                .eq(SysRolePo::getStatus, STATUS_ENABLED)
                .eq(SysRolePo::getDeleted, DELETED_NO)
                .last("limit 1"));
        if (role == null) {
            return List.of();
        }
        List<Long> userIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRolePo>()
                        .eq(SysUserRolePo::getRoleId, role.getId()))
                .stream().map(SysUserRolePo::getUserId).distinct().toList();
        if (userIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<SysUserPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserPo::getTenantId, tenantId);
        wrapper.eq(SysUserPo::getStatus, STATUS_ENABLED);
        wrapper.eq(SysUserPo::getDeleted, DELETED_NO);
        wrapper.in(SysUserPo::getId, userIds);
        wrapper.orderByAsc(SysUserPo::getId);
        wrapper.last("limit " + limit);
        return userMapper.selectList(wrapper).stream().map(UserPersistenceAssembler::toDomain).toList();
    }

    @Override
    public List<Long> findEnabledIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<SysUserPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(SysUserPo::getId);
        wrapper.in(SysUserPo::getId, ids);
        wrapper.eq(SysUserPo::getStatus, STATUS_ENABLED);
        // 服务间成员校验：有租户上下文时限制在同租户内，无上下文时保持既有的全局行为（Phase 3 收口）
        wrapper.eq(TenantContext.get() != null, SysUserPo::getTenantId, TenantContext.get());
        return userMapper.selectList(wrapper).stream().map(SysUserPo::getId).toList();
    }

    /**
     * 查询指定租户的全部角色 ID（逻辑删除的角色自动排除）
     *
     * @param tenantId 租户 ID
     * @return 角色 ID 列表
     */
    private List<Long> listTenantRoleIds(Long tenantId) {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRolePo>()
                        .select(SysRolePo::getId)
                        .eq(SysRolePo::getTenantId, tenantId))
                .stream().map(SysRolePo::getId).toList();
    }

    /**
     * LIKE 关键字转义：反斜杠、百分号、下划线（MySQL LIKE 默认以反斜杠为转义符），
     * 防止用户输入的通配符被当作模式展开导致全表扫描
     *
     * @param keyword 原始关键字
     * @return 转义后的关键字
     */
    private static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    @Override
    public void deleteById(Long id) {
        userMapper.delete(new LambdaQueryWrapper<SysUserPo>()
                .eq(SysUserPo::getId, id)
                .eq(SysUserPo::getTenantId, TenantContext.requireId()));
        // sys_user 走全局逻辑删除（logic-delete-field: deleted）；本方法为账号级删除语义
        // （仅首页租户可删自己的账号），角色关联为物理删除、跨租户一并清理，语义一致
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRolePo>().eq(SysUserRolePo::getUserId, id));
    }
}
