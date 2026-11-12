package com.bluewhale.auth.interfaces.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 用户配置写入请求（内网端点）。
 *
 * <p>供 whale-scene 等业务服务把「用户偏好」落到 auth 统一配置表。
 * auth 只做存储安全校验（键口径、JSON 合法性、大小上限），
 * 业务语义由调用方自行校验。</p>
 *
 * @param tenantId    租户 ID（内网调用必须显式携带，且须与 {@code X-Tenant-Id} 头一致）
 * @param userId      目标用户 ID
 * @param configKey   配置键（反域名小写点号口径，如 {@code scene.view}）
 * @param configValue 配置值（JSON 文本）
 * @param operatorId  操作人用户 ID（为空时记为 userId 本人）
 * @author BlueWhale
 */
public record ConfigSetRequest(

        @NotNull(message = "tenantId 不能为空")
        Long tenantId,

        @NotNull(message = "userId 不能为空")
        Long userId,

        @NotBlank(message = "configKey 不能为空")
        String configKey,

        @NotBlank(message = "configValue 不能为空")
        String configValue,

        Long operatorId
) {
}
