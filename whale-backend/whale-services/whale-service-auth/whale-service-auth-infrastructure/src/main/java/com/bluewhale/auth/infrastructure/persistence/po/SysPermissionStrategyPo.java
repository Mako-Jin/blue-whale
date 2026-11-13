package com.bluewhale.auth.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 权限策略实体（权限中心扩展 M3：对应 {@code sys_permission_strategy}，条件化授权/拒绝）。
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_permission_strategy")
public class SysPermissionStrategyPo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 租户 ID */
    private Long tenantId;
    /** 策略编码（租户内唯一） */
    private String strategyCode;
    /** 策略名称 */
    private String strategyName;
    /** 策略类型：GRANT 授权 / DENY 拒绝 */
    private String strategyType;
    /** 生效角色 ID 集合（JSON 数组：[1,2]） */
    private String roleIds;
    /** 生效资源 ID 集合（JSON 数组：[10,20]） */
    private String resourceIds;
    /** 附加条件（JSON 数组，为空表示无条件） */
    private String conditionJson;
    /** 是否默认策略：1 是（同租户仅一条，聚合根保证） */
    private Integer isDefault;
    /** 优先级：数字小者优先 */
    private Integer priority;
    /** 状态：1 启用 / 0 停用 */
    private Integer status;
    /** 描述 */
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
