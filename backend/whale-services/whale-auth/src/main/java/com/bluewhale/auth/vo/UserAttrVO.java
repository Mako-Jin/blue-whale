package com.bluewhale.auth.vo;

import com.bluewhale.auth.domain.permission.aggregate.UserPermissionAttribute;
import com.bluewhale.auth.entity.SysUserPermission;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户 ABAC 属性视图对象
 *
 * @author BlueWhale
 */
@Data
public class UserAttrVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private Long permissionId;
    private String attrKey;
    private String attrValue;

    /**
     * 由用户属性实体转换为视图对象
     *
     * @param entity 用户属性实体
     * @return 用户属性视图对象
     */
    public static UserAttrVO from(SysUserPermission entity) {
        UserAttrVO vo = new UserAttrVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUserId());
        vo.setPermissionId(entity.getPermissionId());
        vo.setAttrKey(entity.getAttrKey());
        vo.setAttrValue(entity.getAttrValue());
        return vo;
    }

    /**
     * 由用户属性聚合根转换为视图对象
     *
     * @param attribute 用户属性聚合根
     * @return 用户属性视图对象
     */
    public static UserAttrVO from(UserPermissionAttribute attribute) {
        UserAttrVO vo = new UserAttrVO();
        vo.setId(attribute.getId());
        vo.setUserId(attribute.getUserId());
        vo.setPermissionId(attribute.getPermissionId());
        vo.setAttrKey(attribute.getAttrKey());
        vo.setAttrValue(attribute.getAttrValue());
        return vo;
    }
}
