package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.auth.domain.permission.aggregate.Permission;
import com.bluewhale.auth.domain.role.aggregate.Role;
import com.bluewhale.auth.domain.role.repository.RoleRepository;
import com.bluewhale.auth.entity.SysPermission;
import com.bluewhale.auth.entity.SysRole;
import com.bluewhale.auth.entity.SysRolePermission;
import com.bluewhale.auth.entity.SysUserRole;
import com.bluewhale.auth.infrastructure.persistence.assembler.RolePersistenceAssembler;
import com.bluewhale.auth.mapper.SysPermissionMapper;
import com.bluewhale.auth.mapper.SysRoleMapper;
import com.bluewhale.auth.mapper.SysRolePermissionMapper;
import com.bluewhale.auth.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@link RoleRepository} 的 MyBatis-Plus 实现（REQ-ARCH-001 / T-DDD-5）：以既有 {@link SysRoleMapper} 完成
 * PO 读写，并借 {@link RolePersistenceAssembler} 完成 PO ↔ 领域对象转换。
 *
 * <p>分页查询在实现内直采 Mapper 的 {@code selectPage} 能力，对领域层只暴露自有分页载体
 * {@link RoleRepository.RolePage}，避免 MyBatis 分页类型泄漏到领域层。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class RoleRepositoryImpl implements RoleRepository {

    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysPermissionMapper permissionMapper;

    @Override
    public void save(Role role) {
        SysRole po = RolePersistenceAssembler.toPo(role);
        if (po.getId() == null) {
            roleMapper.insert(po);
        } else {
            roleMapper.updateById(po);
        }
    }

    @Override
    public Optional<Role> findById(Long id) {
        SysRole po = roleMapper.selectById(id);
        return Optional.ofNullable(po).map(RolePersistenceAssembler::toDomain);
    }

    @Override
    public Optional<Role> findByCode(String roleCode) {
        SysRole po = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode));
        return Optional.ofNullable(po).map(RolePersistenceAssembler::toDomain);
    }

    @Override
    public List<Role> listEnabledByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<SysRole> pos = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .in(SysRole::getId, ids)
                .eq(SysRole::getStatus, Role.STATUS_ENABLED));
        return pos.stream().map(RolePersistenceAssembler::toDomain).toList();
    }

    @Override
    public RolePage page(long current, long size, String keyword) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysRole::getRoleCode, keyword)
                    .or().like(SysRole::getRoleName, keyword));
        }
        wrapper.orderByAsc(SysRole::getSortNo).orderByDesc(SysRole::getCreateTime);
        Page<SysRole> page = roleMapper.selectPage(new Page<>(current, size), wrapper);
        List<Role> records = page.getRecords().stream().map(RolePersistenceAssembler::toDomain).toList();
        return new RolePage(records, page.getTotal());
    }

    @Override
    public boolean existsByCode(String roleCode) {
        Long count = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode));
        return count != null && count > 0;
    }

    @Override
    public long countByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0L;
        }
        Long count = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>().in(SysRole::getId, ids));
        return count == null ? 0L : count;
    }

    @Override
    public void deleteById(Long id) {
        roleMapper.deleteById(id);
        // 关联表实体无 deleted 字段，走物理删除
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, id));
    }

    @Override
    public List<Long> listRoleIdsByUserId(Long userId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).toList();
    }

    @Override
    public List<Long> listPermissionIdsByRoleIds(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return rolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                        .in(SysRolePermission::getRoleId, roleIds))
                .stream().map(SysRolePermission::getPermissionId).distinct().toList();
    }

    @Override
    public List<String> listEnabledPermissionCodesByIds(Collection<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return List.of();
        }
        return permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                        .in(SysPermission::getId, permissionIds)
                        .eq(SysPermission::getStatus, Permission.STATUS_ENABLED))
                .stream().map(SysPermission::getPermCode).distinct().toList();
    }

    @Override
    public void replacePermissions(Long roleId, List<Long> permissionIds) {
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, roleId));
        for (Long permissionId : permissionIds) {
            SysRolePermission relation = new SysRolePermission();
            relation.setRoleId(roleId);
            relation.setPermissionId(permissionId);
            rolePermissionMapper.insert(relation);
        }
    }

    @Override
    public List<Long> listPermissionIds(Long roleId) {
        return rolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                        .eq(SysRolePermission::getRoleId, roleId))
                .stream().map(SysRolePermission::getPermissionId).toList();
    }
}
