package com.bluewhale.config.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 配置项创建请求
 *
 * @author BlueWhale
 */
@Data
public class ConfigItemCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 配置键（租户内唯一） */
    @NotBlank(message = "{validation.config.config-item.config-key-required}")
    @Size(max = 128, message = "{validation.config.config-item.config-key-size}")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$", message = "{validation.config.config-item.config-key-pattern}")
    private String configKey;

    /** 配置值 */
    @Size(max = 65535, message = "{validation.config.config-item.config-value-size}")
    private String configValue;

    /** 值类型：STRING / NUMBER / BOOLEAN / JSON，默认 STRING */
    @Pattern(regexp = "^(?i)(STRING|NUMBER|BOOLEAN|JSON)$", message = "{validation.config.config-item.value-type-pattern}")
    @Size(max = 16, message = "{validation.config.config-item.value-type-size}")
    private String valueType;

    /** 配置说明 */
    @Size(max = 255, message = "{validation.config.config-item.description-size}")
    private String description;

    /** 状态：1 启用 / 0 停用，默认 1 */
    private Integer status;
}
