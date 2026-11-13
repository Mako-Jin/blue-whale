package com.bluewhale.auth.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色约束实体（权限中心扩展 M3：对应 {@code sys_role_constraint}，互斥/基数约束）。
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_role_constraint")
public class SysRoleConstraintPo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 租户 ID */
    private Long tenantId;
    /** 约束编码（租户内唯一） */
    private String constraintCode;
    /** 约束名称 */
    private String constraintName;
    /** 约束类型：MUTEX 互斥 / CARDINALITY 基数 */
    private String constraintType;
    /** 约束覆盖的角色 ID 集合（JSON 数组） */
    private String roleIds;
    /** 最多可同时分配的角色数（仅 CARDINALITY，必须 > 0） */
    private Integer maxAssign;
    /** 状态：1 启用 / 0 停用 */
    private Integer status;
    /** 描述 */
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
