package com.bluewhale.auth.domain.permission.aggregate;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.valueobject.ValueObject;

/**
 * 用户 ABAC 属性聚合的业务标识值对象：{@code (userId, permissionId, attrKey)} 三元组。
 *
 * <p>对应 {@code sys_user_permission.uk_user_perm_attr(user_id, permission_id, attr_key)}。
 * 注意与持久化自增主键（{@link UserPermissionAttribute#getRowId()}）区分：业务标识与持久化标识是两个概念。</p>
 *
 * @param userId 用户 ID
 * @param permissionId 权限点 ID
 * @param attrKey 属性键
 * @author BlueWhale
 */
public record UserPermissionKey(Long userId, Long permissionId, String attrKey) implements ValueObject {

    /**
     * 紧凑构造器：校验三元组各分量非空
     *
     * @param userId 用户 ID
     * @param permissionId 权限点 ID
     * @param attrKey 属性键
     */
    public UserPermissionKey {
        if (userId == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "用户 ID 不能为空");
        }
        if (permissionId == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "权限点 ID 不能为空");
        }
        if (attrKey == null || attrKey.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "属性键不能为空");
        }
    }
}
