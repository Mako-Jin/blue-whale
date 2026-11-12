package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 部门负责人响应（供 OA 审批中心内网解析「部门经理」）
 *
 * @author BlueWhale
 */
@Data
public class DeptLeaderResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 部门 ID */
    private Long deptId;

    /** 部门名称 */
    private String deptName;

    /** 负责人用户 ID；未维护时为 {@code null} */
    private Long leaderUserId;
}
