package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户 ABAC 属性响应
 *
 * @author BlueWhale
 */
@Data
public class UserAttrResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private Long permissionId;
    private String attrKey;
    private String attrValue;
}
