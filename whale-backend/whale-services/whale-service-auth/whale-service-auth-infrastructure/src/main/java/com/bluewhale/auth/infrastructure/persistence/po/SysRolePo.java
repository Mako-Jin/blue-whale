package com.bluewhale.auth.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统角色实体
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_role")
public class SysRolePo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 所属租户ID（逻辑关联 sys_tenant.id） */
    private Long tenantId;
    private String roleCode;
    private String roleName;
    private String description;
    private Integer sortNo;
    /** 状态：1 启用 / 0 停用 */
    private Integer status;
    /** 父角色 ID（0 表示顶级，继承链不得成环） */
    private Long parentId;
    /** 角色层级（顶级为 1，子角色只能父 +1） */
    private Integer roleLevel;
    /** 继承策略：FULL 全量 / INCREMENT 增量 / OVERRIDE 覆盖 / NONE 不继承 */
    private String inheritStrategy;
    /** 角色分类：GENERAL 通用 / BUSINESS 业务 / SYSTEM 系统 */
    private String category;
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
