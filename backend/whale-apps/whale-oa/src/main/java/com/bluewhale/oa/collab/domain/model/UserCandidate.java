package com.bluewhale.oa.collab.domain.model;

import lombok.Data;

/**
 * 公告接收人候选（来自 Auth 内网通讯录）。
 *
 * @author BlueWhale
 */
@Data
public class UserCandidate {

    /** 用户ID */
    private Long userId;

    /** 账号 */
    private String username;

    /** 姓名 */
    private String nickname;

    /** 部门ID（通讯录未返回时为空） */
    private Long deptId;
}
