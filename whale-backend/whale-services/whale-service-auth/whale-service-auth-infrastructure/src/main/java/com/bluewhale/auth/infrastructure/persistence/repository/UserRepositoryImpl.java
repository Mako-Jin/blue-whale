package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.auth.domain.user.aggregate.User;
import com.bluewhale.auth.domain.user.entity.UserRoleAssignment;
import com.bluewhale.auth.domain.user.repository.UserRepository;
import com.bluewhale.auth.infrastructure.persistence.entity.SysRole;
import com.bluewhale.auth.infrastructure.persistence.entity.SysUser;
import com.bluewhale.auth.infrastructure.persistence.entity.SysUserRole;
import com.bluewhale.auth.infrastructure.persistence.assembler.UserPersistenceAssembler;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysRoleMapper;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysUserMapper;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysUserRoleMapper;
import com.bluewhale.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

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
    public User save(User user) {
        SysUser po = UserPersistenceAssembler.toPo(user);
        if (po.getId() == null) {
            userMapper.insert(po);
        } else {
            userMapper.updateById(po);
        }
        return UserPersistenceAssembler.toDomain(po);
    }

    @Override
    public Optional<User> findById(Long id) {
        SysUser po = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getId, id)
                .eq(SysUser::getTenantId, TenantContext.requireId()));
        return Optional.ofNullable(po).map(UserPersistenceAssembler::toDomain);
    }

    @Override
    public Optional<User> findByIdAcrossTenants(Long id) {
        // sys_user 归属首页租户，令牌租户可能是切换后的其他租户：
        // 临时清空租户上下文以绕过行级过滤，读取后立即恢复原值（含 null）
        Long previous = TenantContext.get();
        TenantContext.clear();
        try {
            SysUser po = userMapper.selectById(id);
            return Optional.ofNullable(po).map(UserPersistenceAssembler::toDomain);
        } finally {
            TenantContext.set(previous);
        }
    }

    @Override
    public Optional<User> findByUsername(String username) {
        SysUser po = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, TenantContext.requireId())
                .eq(SysUser::getUsername, username)
                .last("limit 1"));
        return Optional.ofNullable(po).map(UserPersistenceAssembler::toDomain);
    }

    @Override
    public Optional<User> findByTenantIdAndUsername(Long tenantId, String username) {
        SysUser po = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, tenantId)
                .eq(SysUser::getUsername, username)
                .last("limit 1"));
        return Optional.ofNullable(po).map(UserPersistenceAssembler::toDomain);
    }

    @Override
    public boolean existsByUsername(Long tenantId, String username) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, tenantId)
                .eq(SysUser::getUsername, username));
        return count != null && count > 0;
    }

    @Override
    public void replaceRoles(Long userId, List<Long> roleIds) {
        // 一人多租户：只清理当前租户角色的关联行，避免误删该用户在其他租户的角色
        Long tenantId = TenantContext.requireId();
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId)
                .inSql(SysUserRole::getRoleId,
                        "SELECT id FROM sys_role WHERE tenant_id = " + tenantId));
        for (Long roleId : roleIds) {
            userRoleMapper.insert(UserPersistenceAssembler.toAssignmentPo(UserRoleAssignment.of(userId, roleId)));
        }
    }

    @Override
    public List<Long> listRoleIds(Long userId) {
        // 仅返回当前租户内的角色关联（跨租户角色不参与本租户的角色管理展示）
        Long tenantId = TenantContext.requireId();
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)
                        .inSql(SysUserRole::getRoleId,
                                "SELECT id FROM sys_role WHERE tenant_id = " + tenantId))
                .stream().map(SysUserRole::getRoleId).toList();
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
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getTenantId, TenantContext.requireId());
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getNickname, keyword)
                    .or().like(SysUser::getPhone, keyword));
        }
        wrapper.orderByDesc(SysUser::getCreateTime);
        Page<SysUser> page = userMapper.selectPage(new Page<>(current, size), wrapper);
        List<User> records = page.getRecords().stream().map(UserPersistenceAssembler::toDomain).toList();
        return new UserPage(records, page.getTotal());
    }

    @Override
    public UserPage directoryPage(long current, long size, Collection<Long> deptIds, String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getTenantId, TenantContext.requireId());
        wrapper.eq(SysUser::getStatus, STATUS_ENABLED);
        wrapper.eq(SysUser::getDeleted, DELETED_NO);
        if (deptIds != null) {
            // 空集合走 in 会生成 `in ()` 的非法 SQL，直接给一个恒假条件
            if (deptIds.isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in(SysUser::getDeptId, deptIds);
            }
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getNickname, keyword)
                    .or().like(SysUser::getPhone, keyword));
        }
        wrapper.orderByAsc(SysUser::getId);
        Page<SysUser> page = userMapper.selectPage(new Page<>(current, size), wrapper);
        List<User> records = page.getRecords().stream().map(UserPersistenceAssembler::toDomain).toList();
        return new UserPage(records, page.getTotal());
    }

    @Override
    public List<User> findByRoleCode(String roleCode, int limit) {
        if (!StringUtils.hasText(roleCode)) {
            return List.of();
        }
        Long tenantId = TenantContext.requireId();
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, tenantId)
                .eq(SysRole::getRoleCode, roleCode)
                .eq(SysRole::getStatus, STATUS_ENABLED)
                .eq(SysRole::getDeleted, DELETED_NO)
                .last("limit 1"));
        if (role == null) {
            return List.of();
        }
        List<Long> userIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getRoleId, role.getId()))
                .stream().map(SysUserRole::getUserId).distinct().toList();
        if (userIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getTenantId, tenantId);
        wrapper.eq(SysUser::getStatus, STATUS_ENABLED);
        wrapper.eq(SysUser::getDeleted, DELETED_NO);
        wrapper.in(SysUser::getId, userIds);
        wrapper.orderByAsc(SysUser::getId);
        wrapper.last("limit " + limit);
        return userMapper.selectList(wrapper).stream().map(UserPersistenceAssembler::toDomain).toList();
    }

    @Override
    public List<Long> findEnabledIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(SysUser::getId);
        wrapper.in(SysUser::getId, ids);
        wrapper.eq(SysUser::getStatus, STATUS_ENABLED);
        // 服务间成员校验：有租户上下文时限制在同租户内，无上下文时保持既有的全局行为（Phase 3 收口）
        wrapper.eq(TenantContext.get() != null, SysUser::getTenantId, TenantContext.get());
        return userMapper.selectList(wrapper).stream().map(SysUser::getId).toList();
    }

    @Override
    public void deleteById(Long id) {
        userMapper.delete(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getId, id)
                .eq(SysUser::getTenantId, TenantContext.requireId()));
        // 关联表实体无 deleted 字段，走物理删除
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
    }
}
