package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.auth.domain.aggregate.Permission;
import com.bluewhale.auth.domain.aggregate.Role;
import com.bluewhale.auth.domain.repository.RoleRepository;
import com.bluewhale.auth.infrastructure.persistence.entity.SysPermission;
import com.bluewhale.auth.infrastructure.persistence.entity.SysRole;
import com.bluewhale.auth.infrastructure.persistence.entity.SysRolePermission;
import com.bluewhale.auth.infrastructure.persistence.entity.SysUserRole;
import com.bluewhale.auth.infrastructure.persistence.assembler.RolePersistenceAssembler;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysPermissionMapper;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysRoleMapper;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysRolePermissionMapper;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysUserRoleMapper;
import com.bluewhale.common.tenant.TenantContext;
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
    public Role save(Role role) {
        SysRole po = RolePersistenceAssembler.toPo(role);
        if (po.getId() == null) {
            roleMapper.insert(po);
        } else {
            roleMapper.updateById(po);
        }
        return RolePersistenceAssembler.toDomain(po);
    }

    @Override
    public Optional<Role> findById(Long id) {
        SysRole po = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getId, id)
                .eq(SysRole::getTenantId, TenantContext.requireId()));
        return Optional.ofNullable(po).map(RolePersistenceAssembler::toDomain);
    }

    @Override
    public Optional<Role> findByCode(String roleCode) {
        SysRole po = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, TenantContext.requireId())
                .eq(SysRole::getRoleCode, roleCode));
        return Optional.ofNullable(po).map(RolePersistenceAssembler::toDomain);
    }

    @Override
    public Optional<Role> findByTenantIdAndCode(Long tenantId, String roleCode) {
        SysRole po = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, tenantId)
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
        wrapper.eq(SysRole::getTenantId, TenantContext.requireId());
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
                .eq(SysRole::getTenantId, TenantContext.requireId())
                .eq(SysRole::getRoleCode, roleCode));
        return count != null && count > 0;
    }

    @Override
    public long countByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0L;
        }
        Long count = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, TenantContext.requireId())
                .in(SysRole::getId, ids));
        return count == null ? 0L : count;
    }

    @Override
    public void deleteById(Long id) {
        roleMapper.delete(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getId, id)
                .eq(SysRole::getTenantId, TenantContext.requireId()));
        // 关联表实体无 deleted 字段，走物理删除
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, id));
    }

    @Override
    public List<Long> listRoleIdsByUserId(Long userId) {
        // 一人多租户（Wave 5.54 Phase 5.1）：sys_user_role 无 tenant_id，
        // 必须 join sys_role 按当前租户 + 启用状态收口，禁止把其他租户的角色带入权限计算
        Long tenantId = TenantContext.requireId();
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)
                        .inSql(SysUserRole::getRoleId,
                                "SELECT id FROM sys_role WHERE tenant_id = " + tenantId
                                        + " AND status = " + Role.STATUS_ENABLED + " AND deleted = 0"))
                .stream().map(SysUserRole::getRoleId).toList();
    }

    @Override
    public List<Long> listPermissionIdsByRoleIds(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        // 防御性租户收口：即使调用方传入跨租户角色 ID，也只解析当前租户内的角色
        Long tenantId = TenantContext.requireId();
        return rolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                        .in(SysRolePermission::getRoleId, roleIds)
                        .inSql(SysRolePermission::getRoleId,
                                "SELECT id FROM sys_role WHERE tenant_id = " + tenantId
                                        + " AND deleted = 0"))
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

    @Override
    public List<String> listRoleCodesByUserId(Long userId) {
        List<Long> roleIds = this.listRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return this.listEnabledByIds(roleIds).stream()
                .map(role -> role.getRoleCode().value())
                .toList();
    }

    @Override
    public List<String> listPermissionCodesByUserId(Long userId) {
        List<Long> roleIds = this.listRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> permissionIds = this.listPermissionIdsByRoleIds(roleIds);
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        return this.listEnabledPermissionCodesByIds(permissionIds);
    }
}
