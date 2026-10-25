package com.bluewhale.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统部门实体
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_dept")
public class SysDept implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 所属租户ID（逻辑关联 sys_tenant.id） */
    private Long tenantId;
    /** 父部门ID，0 表示顶级 */
    private Long parentId;
    private String deptName;
    private Integer sort;
    private String leader;
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
