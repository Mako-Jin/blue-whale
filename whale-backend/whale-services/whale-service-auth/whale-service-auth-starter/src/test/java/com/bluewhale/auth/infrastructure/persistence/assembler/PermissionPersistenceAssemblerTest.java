package com.bluewhale.auth.infrastructure.persistence.assembler;

import com.bluewhale.auth.domain.model.vo.PermCode;
import com.bluewhale.auth.domain.aggregate.PermissionAggregate;
import com.bluewhale.auth.infrastructure.persistence.entity.SysPermission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link PermissionPersistenceAssembler} 逐字段比对单元测试（REQ-ARCH-001 / T-DDD-6）：
 * 防止 PO ↔ 领域对象转换漏字段。
 *
 * @author BlueWhale
 */
class PermissionPersistenceAssemblerTest {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 1, 1, 9, 0, 0);
    private static final LocalDateTime UPDATED = LocalDateTime.of(2026, 1, 2, 9, 0, 0);

    private static SysPermission fullPo() {
        SysPermission po = new SysPermission();
        po.setId(5L);
        po.setParentId(3L);
        po.setPermCode("sys:user:list");
        po.setPermName("用户列表");
        po.setPermType("API");
        po.setPath("/api/user");
        po.setMethod("GET");
        po.setSortNo(2);
        po.setStatus(PermissionAggregate.STATUS_ENABLED);
        po.setDeleted(0);
        po.setCreateTime(CREATED);
        po.setUpdateTime(UPDATED);
        return po;
    }

    private static PermissionAggregate fullDomain() {
        return PermissionAggregate.reconstitute(5L, 3L, PermCode.of("sys:user:list"), "用户列表", "API",
                "/api/user", "GET", 2, PermissionAggregate.STATUS_ENABLED, CREATED, UPDATED);
    }

    @Test
    @DisplayName("PO → 领域：Permission 聚合全部关键字段逐项一致")
    void shouldMapPoToDomainFieldByField() {
        PermissionAggregate permission = PermissionPersistenceAssembler.toDomain(fullPo());

        assertEquals(5L, permission.getId());
        assertEquals(3L, permission.getParentId());
        assertEquals("sys:user:list", permission.getPermCode().value());
        assertEquals("用户列表", permission.getPermName());
        assertEquals("API", permission.getPermType());
        assertEquals("/api/user", permission.getPath());
        assertEquals("GET", permission.getMethod());
        assertEquals(2, permission.getSortNo());
        assertEquals(PermissionAggregate.STATUS_ENABLED, permission.getStatus());
        assertEquals(CREATED, permission.getCreateTime());
        assertEquals(UPDATED, permission.getUpdateTime());
    }

    @Test
    @DisplayName("领域 → PO：SysPermission 各字段逐项一致，未纳入聚合的字段保持为空")
    void shouldMapDomainToPoFieldByField() {
        SysPermission po = PermissionPersistenceAssembler.toPo(fullDomain());

        assertEquals(5L, po.getId());
        assertEquals(3L, po.getParentId());
        assertEquals("sys:user:list", po.getPermCode());
        assertEquals("用户列表", po.getPermName());
        assertEquals("API", po.getPermType());
        assertEquals("/api/user", po.getPath());
        assertEquals("GET", po.getMethod());
        assertEquals(2, po.getSortNo());
        assertEquals(PermissionAggregate.STATUS_ENABLED, po.getStatus());
        assertEquals(CREATED, po.getCreateTime());
        assertEquals(UPDATED, po.getUpdateTime());
        // deleted 不属本步 Permission 聚合关键项，转换不携带
        assertNull(po.getDeleted());
    }

    @Test
    @DisplayName("PO → 领域：parentId/sortNo/status 为空按 0 / 0 / 启用归一化")
    void shouldNormalizeNullFields() {
        SysPermission po = fullPo();
        po.setParentId(null);
        po.setSortNo(null);
        po.setStatus(null);

        PermissionAggregate permission = PermissionPersistenceAssembler.toDomain(po);

        assertEquals(PermissionAggregate.ROOT_PARENT_ID, permission.getParentId());
        assertEquals(0, permission.getSortNo());
        assertEquals(PermissionAggregate.STATUS_ENABLED, permission.getStatus());
    }
}
