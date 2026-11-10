package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 「我的租户」列表项响应（Wave 5.54 Phase 5.1：一人多租户切换器数据源）。
 *
 * @author BlueWhale
 */
@Data
public class TenantResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long tenantId;
    private String tenantCode;
    private String tenantName;
    /** 是否为当前令牌所在租户 */
    private boolean current;
}
