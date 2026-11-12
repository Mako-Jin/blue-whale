package com.bluewhale.auth.infrastructure.persistence.assembler;

import com.bluewhale.auth.domain.model.vo.PermCode;
import com.bluewhale.auth.domain.aggregate.PermissionAggregate;
import com.bluewhale.auth.infrastructure.persistence.po.SysPermissionPo;

/**
 * Permission 聚合与其持久化对象（PO）的双向转换器（手写 Assembler，REQ-ARCH-001 / T-DDD-6）。
 *
 * <p>选择手写而非 MapStruct：权限码以值对象（{@link PermCode}）承载，且需对
 * {@code parentId}/{@code sortNo}/{@code status} 做空值归一化，显式转换更可控、可读。
 * 未映射字段 {@code deleted} 不属于 Permission 聚合关键项，转换时保持 PO 原值不动。</p>
 *
 * @author BlueWhale
 */
public final class PermissionPersistenceAssembler {

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private PermissionPersistenceAssembler() {
    }

    /**
     * PO → 领域对象：{@code parentId}/{@code sortNo}/{@code status} 为空时按 0 / 0 / 启用归一化
     *
     * @param po 权限点持久化对象
     * @return 权限点聚合根
     */
    public static PermissionAggregate toDomain(SysPermissionPo po) {
        return PermissionAggregate.reconstitute(
                po.getId(),
                po.getParentId(),
                PermCode.of(po.getPermCode()),
                po.getPermName(),
                po.getPermType(),
                po.getPath(),
                po.getMethod(),
                po.getSortNo(),
                po.getStatus(),
                po.getCreateTime(),
                po.getUpdateTime());
    }

    /**
     * 领域对象 → PO：仅转换 Permission 聚合所承载的字段
     *
     * @param permission 权限点聚合根
     * @return 权限点持久化对象
     */
    public static SysPermissionPo toPo(PermissionAggregate permission) {
        SysPermissionPo po = new SysPermissionPo();
        po.setId(permission.getId());
        po.setParentId(permission.getParentId());
        po.setPermCode(permission.getPermCode().value());
        po.setPermName(permission.getPermName());
        po.setPermType(permission.getPermType());
        po.setPath(permission.getPath());
        po.setMethod(permission.getMethod());
        po.setSortNo(permission.getSortNo());
        po.setStatus(permission.getStatus());
        po.setCreateTime(permission.getCreateTime());
        po.setUpdateTime(permission.getUpdateTime());
        return po;
    }
}
