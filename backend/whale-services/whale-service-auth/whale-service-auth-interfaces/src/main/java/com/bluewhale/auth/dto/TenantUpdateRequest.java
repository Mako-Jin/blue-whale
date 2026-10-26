package com.bluewhale.auth.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 租户更新请求：名称与配额（均为可选，传值即覆盖）。
 *
 * @author BlueWhale
 */
@Data
public class TenantUpdateRequest {

    @Size(max = 128, message = "租户名称长度不能超过 128")
    private String tenantName;

    @Size(max = 1024, message = "配额 JSON 长度不能超过 1024")
    private String quotaJson;
}
