package com.bluewhale.auth.infrastructure.persistence.assembler;

import com.bluewhale.auth.domain.model.vo.RoleCode;
import com.bluewhale.auth.domain.role.aggregate.Role;
import com.bluewhale.auth.domain.role.entity.RolePermissionAssignment;
import com.bluewhale.auth.entity.SysRole;
import com.bluewhale.auth.entity.SysRolePermission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link RolePersistenceAssembler} 逐字段比对单元测试（REQ-ARCH-001 / T-DDD-5）：
 * 防止 PO ↔ 领域对象转换漏字段。
 *
 * @author BlueWhale
 */
class RolePersistenceAssemblerTest {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 1, 1, 9, 0, 0);
    private static final LocalDateTime UPDATED = LocalDateTime.of(2026, 1, 2, 9, 0, 0);

    private static SysRole fullPo() {
        SysRole po = new SysRole();
        po.setId(5L);
        po.setTenantId(1L);
        po.setRoleCode("test_role");
        po.setRoleName("测试角色");
        po.setDescription("描述");
        po.setSortNo(3);
        po.setStatus(Role.STATUS_ENABLED);
        po.setDeleted(0);
        po.setCreateTime(CREATED);
        po.setUpdateTime(UPDATED);
        return po;
    }

    private static Role fullDomain() {
        return Role.reconstitute(5L, 1L, RoleCode.of("test_role"), "测试角色", "描述", 3,
                Role.STATUS_ENABLED, CREATED, UPDATED);
    }

    @Test
    @DisplayName("PO → 领域：Role 聚合全部关键字段逐项一致")
    void shouldMapPoToDomainFieldByField() {
        Role role = RolePersistenceAssembler.toDomain(fullPo());

        assertEquals(5L, role.getId());
        assertEquals("test_role", role.getRoleCode().value());
        assertEquals("测试角色", role.getRoleName());
        assertEquals("描述", role.getDescription());
        assertEquals(3, role.getSortNo());
        assertEquals(Role.STATUS_ENABLED, role.getStatus());
        assertEquals(CREATED, role.getCreateTime());
        assertEquals(UPDATED, role.getUpdateTime());
    }

    @Test
    @DisplayName("领域 → PO：SysRole 各字段逐项一致，未纳入聚合的字段保持为空")
    void shouldMapDomainToPoFieldByField() {
        SysRole po = RolePersistenceAssembler.toPo(fullDomain());

        assertEquals(5L, po.getId());
        assertEquals("test_role", po.getRoleCode());
        assertEquals("测试角色", po.getRoleName());
        assertEquals("描述", po.getDescription());
        assertEquals(3, po.getSortNo());
        assertEquals(Role.STATUS_ENABLED, po.getStatus());
        assertEquals(CREATED, po.getCreateTime());
        assertEquals(UPDATED, po.getUpdateTime());
        // deleted 不属本步 Role 聚合关键项，转换不携带
        assertNull(po.getDeleted());
    }

    @Test
    @DisplayName("PO → 领域：sortNo/status 为空归一化为 0 / 启用")
    void shouldNormalizeNullSortAndStatus() {
        SysRole po = fullPo();
        po.setSortNo(null);
        po.setStatus(null);

        Role role = RolePersistenceAssembler.toDomain(po);

        assertEquals(0, role.getSortNo());
        assertEquals(Role.STATUS_ENABLED, role.getStatus());
    }

    @Test
    @DisplayName("角色-权限分配：PO ↔ 领域双向字段一致")
    void shouldMapPermissionAssignmentBothWays() {
        SysRolePermission po = new SysRolePermission();
        po.setId(8L);
        po.setRoleId(5L);
        po.setPermissionId(6L);

        RolePermissionAssignment assignment = RolePersistenceAssembler.toAssignment(po);

        assertEquals(8L, assignment.id());
        assertEquals(5L, assignment.roleId());
        assertEquals(6L, assignment.permissionId());

        SysRolePermission converted = RolePersistenceAssembler.toAssignmentPo(RolePermissionAssignment.of(5L, 6L));
        assertEquals(5L, converted.getRoleId());
        assertEquals(6L, converted.getPermissionId());
        assertNull(converted.getId());
    }
}
