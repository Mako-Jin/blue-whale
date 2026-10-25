package com.bluewhale.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 租户实体（Wave 5.54 Phase 0 最小租户模型）
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
    /** 状态：1 启用 / 0 停用 */
    private Integer status;
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
