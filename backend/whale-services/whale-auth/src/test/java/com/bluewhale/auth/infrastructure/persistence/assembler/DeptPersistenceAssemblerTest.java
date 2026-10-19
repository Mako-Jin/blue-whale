package com.bluewhale.auth.infrastructure.persistence.assembler;

import com.bluewhale.auth.domain.dept.aggregate.Dept;
import com.bluewhale.auth.entity.SysDept;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link DeptPersistenceAssembler} 逐字段比对单元测试（REQ-ARCH-001 / T-DDD-7）：
 * 防止 PO ↔ 领域对象转换漏字段。
 *
 * @author BlueWhale
 */
class DeptPersistenceAssemblerTest {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 1, 1, 9, 0, 0);
    private static final LocalDateTime UPDATED = LocalDateTime.of(2026, 1, 2, 9, 0, 0);

    private static SysDept fullPo() {
        SysDept po = new SysDept();
        po.setId(5L);
        po.setParentId(3L);
        po.setDeptName("研发部");
        po.setSort(2);
        po.setLeader("张三");
        po.setDeleted(0);
        po.setCreateTime(CREATED);
        po.setUpdateTime(UPDATED);
        return po;
    }

    private static Dept fullDomain() {
        return Dept.reconstitute(5L, 3L, "研发部", 2, "张三", CREATED, UPDATED);
    }

    @Test
    @DisplayName("PO → 领域：Dept 聚合全部关键字段逐项一致")
    void shouldMapPoToDomainFieldByField() {
        Dept dept = DeptPersistenceAssembler.toDomain(fullPo());

        assertEquals(5L, dept.getId());
        assertEquals(3L, dept.getParentId());
        assertEquals("研发部", dept.getDeptName());
        assertEquals(2, dept.getSort());
        assertEquals("张三", dept.getLeader());
        assertEquals(CREATED, dept.getCreateTime());
        assertEquals(UPDATED, dept.getUpdateTime());
    }

    @Test
    @DisplayName("领域 → PO：SysDept 各字段逐项一致，未纳入聚合的字段保持为空")
    void shouldMapDomainToPoFieldByField() {
        SysDept po = DeptPersistenceAssembler.toPo(fullDomain());

        assertEquals(5L, po.getId());
        assertEquals(3L, po.getParentId());
        assertEquals("研发部", po.getDeptName());
        assertEquals(2, po.getSort());
        assertEquals("张三", po.getLeader());
        assertEquals(CREATED, po.getCreateTime());
        assertEquals(UPDATED, po.getUpdateTime());
        // deleted 不属本步 Dept 聚合关键项，转换不携带
        assertNull(po.getDeleted());
    }

    @Test
    @DisplayName("PO → 领域：parentId/sort 为空按 0 / 0 归一化")
    void shouldNormalizeNullFields() {
        SysDept po = fullPo();
        po.setParentId(null);
        po.setSort(null);

        Dept dept = DeptPersistenceAssembler.toDomain(po);

        assertEquals(Dept.ROOT_PARENT_ID, dept.getParentId());
        assertEquals(0, dept.getSort());
    }
}
