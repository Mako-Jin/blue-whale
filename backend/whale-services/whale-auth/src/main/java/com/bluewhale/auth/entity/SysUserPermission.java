package com.bluewhale.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户-权限属性实体（ABAC 数据级属性控制）
 * <p>为「用户 + 权限点」附加属性键值对，供鉴权时做数据范围过滤。</p>
 * <p>注意：关联表不映射 deleted 字段，走物理删除，
 * 避免逻辑删除后与唯一键 uk_user_perm_attr 冲突导致重复写入失败。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_user_permission")
public class SysUserPermission implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private Long permissionId;
    /** 属性键，如 deptId、dataScope */
    private String attrKey;
    /** 属性值，如 100、SELF_DEPT */
    private String attrValue;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
