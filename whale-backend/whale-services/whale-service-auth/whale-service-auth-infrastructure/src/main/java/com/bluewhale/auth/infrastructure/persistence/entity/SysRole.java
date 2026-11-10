package com.bluewhale.auth.infrastructure.persistence.entity;

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
public class SysRole implements Serializable {

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
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
