package com.bluewhale.auth.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户-角色关联实体
 * <p>注意：关联表不映射 deleted 字段，走物理删除，
 * 避免逻辑删除后与唯一键 uk_user_role 冲突导致重复分配失败。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_user_role")
public class SysUserRolePo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private Long roleId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
