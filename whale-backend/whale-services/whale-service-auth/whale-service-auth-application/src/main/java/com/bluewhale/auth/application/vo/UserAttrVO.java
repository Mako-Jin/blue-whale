package com.bluewhale.auth.application.vo;

import com.bluewhale.auth.domain.permission.aggregate.UserPermissionAttribute;
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
