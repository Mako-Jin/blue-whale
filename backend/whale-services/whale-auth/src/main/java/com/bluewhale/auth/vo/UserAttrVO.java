package com.bluewhale.auth.vo;

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

    public static UserAttrVO from(SysUserPermission entity) {
        UserAttrVO vo = new UserAttrVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUserId());
        vo.setPermissionId(entity.getPermissionId());
        vo.setAttrKey(entity.getAttrKey());
        vo.setAttrValue(entity.getAttrValue());
        return vo;
    }
}
