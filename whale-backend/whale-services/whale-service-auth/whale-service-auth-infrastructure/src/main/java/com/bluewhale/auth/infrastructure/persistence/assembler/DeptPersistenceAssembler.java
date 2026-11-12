package com.bluewhale.auth.infrastructure.persistence.assembler;

import com.bluewhale.auth.domain.aggregate.DeptAggregate;
import com.bluewhale.auth.infrastructure.persistence.entity.SysDept;

/**
 * Dept 聚合与其持久化对象（PO）的双向转换器（手写 Assembler，REQ-ARCH-001 / T-DDD-7）。
 *
 * <p>选择手写而非 MapStruct：需对 {@code parentId}/{@code sort} 做空值归一化（与既有 Service 的默认值口径一致），
 * 显式转换更可控、可读。未映射字段 {@code deleted} 不属于 Dept 聚合关键项，转换时保持 PO 原值不动。</p>
 *
 * @author BlueWhale
 */
public final class DeptPersistenceAssembler {

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private DeptPersistenceAssembler() {
    }

    /**
     * PO → 领域对象：{@code parentId}/{@code sort} 为空时按 0 / 0 归一化
     *
     * @param po 部门持久化对象
     * @return 部门聚合根
     */
    public static DeptAggregate toDomain(SysDept po) {
        return DeptAggregate.reconstitute(
                po.getId(),
                po.getTenantId(),
                po.getParentId(),
                po.getDeptName(),
                po.getSort(),
                po.getLeader(),
                po.getLeaderUserId(),
                po.getCreateTime(),
                po.getUpdateTime());
    }

    /**
     * 领域对象 → PO：仅转换 Dept 聚合所承载的字段
     *
     * @param dept 部门聚合根
     * @return 部门持久化对象
     */
    public static SysDept toPo(DeptAggregate dept) {
        SysDept po = new SysDept();
        po.setId(dept.getId());
        po.setTenantId(dept.getTenantId());
        po.setParentId(dept.getParentId());
        po.setDeptName(dept.getDeptName());
        po.setSort(dept.getSort());
        po.setLeader(dept.getLeader());
        po.setLeaderUserId(dept.getLeaderUserId());
        po.setCreateTime(dept.getCreateTime());
        po.setUpdateTime(dept.getUpdateTime());
        return po;
    }
}
