package com.bluewhale.oa.directory.domain.model;

import lombok.Data;

/**
 * 通讯录用户条目。
 *
 * <p>基础账号信息由 Auth 维护，本聚合只做查询侧建模，不落库。</p>
 *
 * @author BlueWhale
 */
@Data
public class DirectoryUser {

    /** 用户ID */
    private Long id;

    /** 登录账号 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 部门ID */
    private Long deptId;

    /** 部门名称 */
    private String deptName;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;
}
