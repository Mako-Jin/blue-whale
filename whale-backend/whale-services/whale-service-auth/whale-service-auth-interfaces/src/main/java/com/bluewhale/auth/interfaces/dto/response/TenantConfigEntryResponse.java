package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 租户配置条目响应
 *
 * @author BlueWhale
 */
@Data
public class TenantConfigEntryResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 租户 ID */
    private Long tenantId;

    /** 配置键（前缀分域） */
    private String configKey;

    /** 配置值（JSON），作为该租户用户的默认值层 */
    private String configValue;
}
