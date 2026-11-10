package com.bluewhale.auth.infrastructure.persistence.assembler;

import com.bluewhale.auth.domain.permission.aggregate.UserPermissionAttribute;
import com.bluewhale.auth.infrastructure.persistence.entity.SysUserPermission;

/**
 * 用户 ABAC 属性聚合与持久化对象的双向转换器（REQ-ARCH-001 / T-DDD-9）。
 *
 * @author BlueWhale
 */
public final class UserPermissionPersistenceAssembler {

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private UserPermissionPersistenceAssembler() {
    }

    /**
     * PO → 领域对象
     *
     * @param po 用户属性持久化对象
     * @return 属性聚合根
     */
    public static UserPermissionAttribute toDomain(SysUserPermission po) {
        return UserPermissionAttribute.reconstitute(po.getId(), po.getUserId(), po.getPermissionId(),
                po.getAttrKey(), po.getAttrValue());
    }

    /**
     * 领域对象 → PO
     *
     * @param attribute 属性聚合根
     * @return 用户属性持久化对象
     */
    public static SysUserPermission toPo(UserPermissionAttribute attribute) {
        SysUserPermission po = new SysUserPermission();
        po.setId(attribute.getId());
        po.setUserId(attribute.getUserId());
        po.setPermissionId(attribute.getPermissionId());
        po.setAttrKey(attribute.getAttrKey());
        po.setAttrValue(attribute.getAttrValue());
        return po;
    }
}
