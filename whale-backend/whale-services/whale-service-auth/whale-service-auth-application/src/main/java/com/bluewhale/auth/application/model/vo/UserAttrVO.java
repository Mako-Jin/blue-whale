package com.bluewhale.auth.application.model.vo;

import com.bluewhale.auth.domain.aggregate.UserPermissionAttributeAggregate;
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
    public static UserAttrVO from(UserPermissionAttributeAggregate attribute) {
        UserAttrVO vo = new UserAttrVO();
        vo.setId(attribute.getRowId());
        vo.setUserId(attribute.getUserId());
        vo.setPermissionId(attribute.getPermissionId());
        vo.setAttrKey(attribute.getAttrKey());
        vo.setAttrValue(attribute.getAttrValue());
        return vo;
    }
}
