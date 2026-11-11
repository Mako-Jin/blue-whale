package com.bluewhale.auth.interfaces.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

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
        List<Long> userIds,

        @NotEmpty(message = "keys 不能为空")
        List<String> keys
) {
}
