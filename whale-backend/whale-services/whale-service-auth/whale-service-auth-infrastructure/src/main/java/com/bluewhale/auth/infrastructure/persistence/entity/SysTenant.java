package com.bluewhale.auth.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 租户实体（Wave 5.54 Phase 1：租户生命周期与配额）
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_tenant")
public class SysTenant implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 租户编码：登录时用于定位租户，全局唯一 */
    private String tenantCode;
    private String tenantName;
    /** 租户类型：PLATFORM 平台运营 / TENANT 业务租户 */
    private String tenantType;
    /** 状态：1 启用 / 0 停用 */
    private Integer status;
    /** 到期时间：NULL 表示长期有效 */
    private LocalDateTime expireTime;
    /** 配额 JSON，如 {"maxUsers":50,"maxStorageMb":1024} */
    private String quotaJson;
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
