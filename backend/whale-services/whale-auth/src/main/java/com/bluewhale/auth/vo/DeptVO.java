package com.bluewhale.auth.vo;

import com.bluewhale.auth.domain.dept.aggregate.Dept;
import com.bluewhale.auth.entity.SysDept;
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
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<DeptVO> children;

    /**
     * 由部门实体转换为视图对象
     *
     * @param dept 部门实体
     * @return 部门视图对象
     */
    public static DeptVO from(SysDept dept) {
        DeptVO vo = new DeptVO();
        vo.setId(dept.getId());
        vo.setParentId(dept.getParentId());
        vo.setDeptName(dept.getDeptName());
        vo.setSort(dept.getSort());
        vo.setLeader(dept.getLeader());
        vo.setCreateTime(dept.getCreateTime());
        vo.setUpdateTime(dept.getUpdateTime());
        return vo;
    }

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
        vo.setCreateTime(dept.getCreateTime());
        vo.setUpdateTime(dept.getUpdateTime());
        return vo;
    }
}
