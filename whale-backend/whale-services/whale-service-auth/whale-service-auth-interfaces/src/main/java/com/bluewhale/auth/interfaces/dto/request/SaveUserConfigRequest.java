package com.bluewhale.auth.interfaces.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 保存用户配置请求。
 *
 * @param configKey   配置键（反域名点号键，如 notify.twin.alert）
 * @param configValue 配置值（合法 JSON，≤ 8192 字符）
 * @author BlueWhale
 */
public record SaveUserConfigRequest(

        @NotBlank(message = "{validation.auth.config.key-required}")
        String configKey,

        @NotBlank(message = "{validation.auth.config.value-required}")
        @Size(max = 8192, message = "{validation.auth.config.value-size}")
        String configValue
) {
}
