package com.bluewhale.auth.interfaces.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 租户更新请求：名称与配额（均为可选，传值即覆盖）。
 *
 * @author BlueWhale
 */
@Data
public class TenantUpdateRequest {

    @Size(max = 128, message = "{validation.auth.tenant.name-size}")
    private String tenantName;

    @Size(max = 1024, message = "{validation.auth.tenant.quota-size}")
    private String quotaJson;
}
