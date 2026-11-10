package com.bluewhale.doc.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 页面级权限授权项。
 *
 * @author BlueWhale
 */
@Data
public class DocAclItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;

    /** 页面角色：READ / EDIT */
    private String aclRole;

    /** 授权时间 */
    private LocalDateTime createTime;
}
