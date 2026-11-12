package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 租户管理响应（平台运营视角，Wave 5.54 Phase 1）
 *
 * @author BlueWhale
 */
@Data
public class TenantAdminResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String tenantCode;
    private String tenantName;
    /** PLATFORM 平台运营 / TENANT 业务租户 */
    private String tenantType;
    /** 1 启用 / 0 停用 */
    private Integer status;
    private LocalDateTime expireTime;
    private String quotaJson;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
