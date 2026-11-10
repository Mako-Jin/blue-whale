package com.bluewhale.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色-权限关联实体
 * <p>注意：关联表不映射 deleted 字段，走物理删除，
 * 避免逻辑删除后与唯一键 uk_role_permission 冲突导致重复分配失败。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_role_permission")
public class SysRolePermission implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long roleId;
    private Long permissionId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
