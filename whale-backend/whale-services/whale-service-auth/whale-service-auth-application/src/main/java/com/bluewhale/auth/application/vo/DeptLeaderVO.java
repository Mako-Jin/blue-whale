package com.bluewhale.auth.application.vo;

import com.bluewhale.auth.domain.dept.aggregate.Dept;
import lombok.Data;

import java.io.Serializable;

/**
 * 部门负责人视图对象（供 OA 审批中心内网解析「部门经理」）
 *
 * @author BlueWhale
 */
@Data
public class DeptLeaderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 部门 ID */
    private Long deptId;

    /** 部门名称 */
    private String deptName;

    /** 负责人用户 ID；未维护时为 {@code null} */
    private Long leaderUserId;

    /**
     * 由部门聚合根转换为负责人视图对象
     *
     * @param dept 部门聚合根
     * @return 部门负责人视图对象
     */
    public static DeptLeaderVO from(Dept dept) {
        DeptLeaderVO vo = new DeptLeaderVO();
        vo.setDeptId(dept.getId());
        vo.setDeptName(dept.getDeptName());
        vo.setLeaderUserId(dept.getLeaderUserId());
        return vo;
    }
}
