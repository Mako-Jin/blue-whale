package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.auth.domain.aggregate.PermissionAggregate;
import com.bluewhale.auth.domain.aggregate.RoleAggregate;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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
    public RoleAggregate save(RoleAggregate role) {
        SysRole po = RolePersistenceAssembler.toPo(role);
        if (po.getId() == null) {
            roleMapper.insert(po);
        } else if (roleMapper.updateById(po) == 0) {
            // 领域对象来自本事务内的 selectOne（已过滤逻辑删除），影响 0 行说明记录已被并发删除
            throw new IllegalStateException("更新角色失败，记录不存在或已被删除: id=" + po.getId());
        }
        return RolePersistenceAssembler.toDomain(po);
    }

    @Override
    public Optional<RoleAggregate> findById(Long id) {
        SysRole po = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getId, id)
                .eq(SysRole::getTenantId, TenantContext.requireId()));
        return Optional.ofNullable(po).map(RolePersistenceAssembler::toDomain);
    }

    @Override
    public Optional<RoleAggregate> findByCode(String roleCode) {
        SysRole po = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, TenantContext.requireId())
                .eq(SysRole::getRoleCode, roleCode));
        return Optional.ofNullable(po).map(RolePersistenceAssembler::toDomain);
    }

    @Override
    public Optional<RoleAggregate> findByTenantIdAndCode(Long tenantId, String roleCode) {
        SysRole po = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, tenantId)
                .eq(SysRole::getRoleCode, roleCode));
        return Optional.ofNullable(po).map(RolePersistenceAssembler::toDomain);
    }

    @Override
    public List<RoleAggregate> listEnabledByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<SysRole> pos = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .in(SysRole::getId, ids)
                .eq(SysRole::getStatus, RoleAggregate.STATUS_ENABLED));
        return pos.stream().map(RolePersistenceAssembler::toDomain).toList();
    }

    @Override
    public RolePage page(long current, long size, String keyword) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRole::getTenantId, TenantContext.requireId());
        if (StringUtils.hasText(keyword)) {
            String escaped = escapeLike(keyword);
            wrapper.and(w -> w.like(SysRole::getRoleCode, escaped)
                    .or().like(SysRole::getRoleName, escaped));
        }
        wrapper.orderByAsc(SysRole::getSortNo).orderByDesc(SysRole::getCreateTime);
        Page<SysRole> page = roleMapper.selectPage(Page.of(current, size), wrapper);
        List<RoleAggregate> records = page.getRecords().stream().map(RolePersistenceAssembler::toDomain).toList();
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
        // 必须按当前租户 + 启用状态收口，禁止把其他租户的角色带入权限计算；
        // 先查本租户启用角色 ID，再以参数化 in() 过滤，不使用 inSql 字符串拼接
        List<Long> tenantRoleIds = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .select(SysRole::getId)
                        .eq(SysRole::getTenantId, TenantContext.requireId())
                        .eq(SysRole::getStatus, RoleAggregate.STATUS_ENABLED))
                .stream().map(SysRole::getId).toList();
        if (tenantRoleIds.isEmpty()) {
            return List.of();
        }
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)
                        .in(SysUserRole::getRoleId, tenantRoleIds))
                .stream().map(SysUserRole::getRoleId).toList();
    }

    @Override
    public List<Long> listPermissionIdsByRoleIds(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        // 防御性租户收口：即使调用方传入跨租户角色 ID，也只解析当前租户内的角色；
        // 先查本租户角色 ID，再以参数化 in() 过滤，不使用 inSql 字符串拼接
        List<Long> tenantRoleIds = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .select(SysRole::getId)
                        .eq(SysRole::getTenantId, TenantContext.requireId()))
                .stream().map(SysRole::getId).toList();
        if (tenantRoleIds.isEmpty()) {
            return List.of();
        }
        return rolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                        .in(SysRolePermission::getRoleId, roleIds)
                        .in(SysRolePermission::getRoleId, tenantRoleIds))
                .stream().map(SysRolePermission::getPermissionId).distinct().toList();
    }

    @Override
    public List<String> listEnabledPermissionCodesByIds(Collection<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return List.of();
        }
        return permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                        .in(SysPermission::getId, permissionIds)
                        .eq(SysPermission::getStatus, PermissionAggregate.STATUS_ENABLED))
                .stream().map(SysPermission::getPermCode).distinct().toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void replacePermissions(Long roleId, List<Long> permissionIds) {
        // 先删后插要求调用方已开启事务（MANDATORY 强制）；
        // 调用方（RoleAppServiceImpl.assignPermissions）已校验 roleId 属于当前租户，
        // 本方法不重复校验
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, roleId));
        for (Long permissionId : permissionIds) {
            SysRolePermission relation = new SysRolePermission();
            relation.setRoleId(roleId);
            relation.setPermissionId(permissionId);
            rolePermissionMapper.insert(relation);
        }
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
