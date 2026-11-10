package com.bluewhale.auth.vo;

import com.bluewhale.auth.domain.dept.aggregate.Dept;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 部门树节点视图对象
 *
 * @author BlueWhale
 */
@Data
public class DeptVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long parentId;
    private String deptName;
    private Integer sort;
    private String leader;
    /** 负责人用户 ID（逻辑关联 sys_user.id，允许为空） */
    private Long leaderUserId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<DeptVO> children;

    /**
     * 由部门聚合根转换为视图对象
     *
     * @param dept 部门聚合根
     * @return 部门视图对象
     */
    public static DeptVO from(Dept dept) {
        DeptVO vo = new DeptVO();
        vo.setId(dept.getId());
        vo.setParentId(dept.getParentId());
        vo.setDeptName(dept.getDeptName());
        vo.setSort(dept.getSort());
        vo.setLeader(dept.getLeader());
        vo.setLeaderUserId(dept.getLeaderUserId());
        vo.setCreateTime(dept.getCreateTime());
        vo.setUpdateTime(dept.getUpdateTime());
        return vo;
    }
}
