package com.bluewhale.auth.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户组实体（权限中心扩展 M3：对应 {@code sys_user_group}，静态组/动态规则组）。
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_user_group")
public class SysUserGroupPo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 租户 ID */
    private Long tenantId;
    /** 组编码（租户内唯一） */
    private String groupCode;
    /** 组名称 */
    private String groupName;
    /** 父组 ID（0 表示顶级，树不得成环） */
    private Long parentId;
    /** 组类别：STATIC 静态（成员显式维护）/ DYNAMIC 动态（成员按 filters 计算） */
    private String category;
    /** 动态组过滤规则（JSON 数组，仅 DYNAMIC 组使用） */
    private String filters;
    /** 描述 */
    private String description;
    /** 排序号 */
    private Integer sortNo;
    /** 状态：1 启用 / 0 停用 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
