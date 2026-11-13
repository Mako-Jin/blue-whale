package com.bluewhale.auth.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户组成员关系实体（权限中心扩展 M3：对应 {@code sys_user_group_member}）。
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_user_group_member")
public class SysUserGroupMemberPo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 租户 ID */
    private Long tenantId;
    /** 用户组 ID */
    private Long groupId;
    /** 成员用户 ID */
    private Long userId;
    /** 成员来源：STATIC 手工维护 / DYNAMIC 规则计算（动态组不可手工改） */
    private String memberSource;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
