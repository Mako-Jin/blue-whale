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

        @NotNull(message = "tenantId 不能为空")
        Long tenantId,

        @NotEmpty(message = "userIds 不能为空")
        @Size(max = 200, message = "userIds 最多 200 个")
        List<Long> userIds,

        @NotEmpty(message = "keys 不能为空")
        @Size(max = 50, message = "keys 最多 50 个")
        List<String> keys
) {
}
