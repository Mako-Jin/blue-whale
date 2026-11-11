package com.bluewhale.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 通知偏好解析请求（内网端点）。
 *
 * @param tenantId          租户 ID
 * @param bizType           业务类型（如 twin.alert；配置键为 notify 前缀 + bizType）
 * @param userIds           待解析收件人（去重后 ≤ 200）
 * @param level             本次消息级别（INFO/WARN/ERROR/CRITICAL/FATAL；空则不做阈值过滤）
 * @param defaults          调用方默认行为：拟发送渠道集与最低级别阈值
 * @param quietBypassLevels 静默豁免级别（如 ["CRITICAL"]；命中则该用户静默窗口不生效）
 * @author BlueWhale
 */
public record PreferenceResolveRequest(

        @NotNull(message = "tenantId 不能为空")
        Long tenantId,

        @NotBlank(message = "bizType 不能为空")
        String bizType,

        @NotEmpty(message = "userIds 不能为空")
        List<Long> userIds,

        String level,

        Defaults defaults,

        List<String> quietBypassLevels
) {

    /**
     * 调用方默认行为。
     *
     * @param channels 拟发送渠道集（如 EMAIL/SMS/IM/INAPP 子集）
     * @param minLevel 最低级别阈值（低于该级别的非站内信渠道被过滤；空则不设阈值）
     */
    public record Defaults(List<String> channels, String minLevel) {
    }
}
