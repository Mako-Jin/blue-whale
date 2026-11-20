package com.bluewhale.auth.interfaces.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 用户配置批量读取请求（内网端点）。
 *
 * @param tenantId 租户 ID（内网调用必须显式携带，与 X-Tenant-Id 头一致）
 * @param userIds  用户 ID 列表（去重后 ≤ 200）
 * @param keys     配置键列表（去重后 ≤ 50）
 * @author BlueWhale
 */
public record ConfigBatchGetRequest(

        @NotNull(message = "{validation.auth.config.tenant-id-required}")
        Long tenantId,

        @NotEmpty(message = "{validation.auth.config.user-ids-required}")
        @Size(max = 200, message = "{validation.auth.config.user-ids-size}")
        List<Long> userIds,

        @NotEmpty(message = "{validation.auth.config.keys-required}")
        @Size(max = 50, message = "{validation.auth.config.keys-size}")
        List<String> keys
) {
}
