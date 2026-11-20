package com.bluewhale.auth.interfaces.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 为用户绑定可进入租户的请求（Wave 5.54 Phase 5.3，仅平台租户）。
 *
 * @author BlueWhale
 */
@Data
public class BindUserTenantRequest {

    /** 目标租户 ID（必须存在且当前可用） */
    @NotNull(message = "{validation.auth.tenant.tenant-id-required}")
    private Long tenantId;
}
