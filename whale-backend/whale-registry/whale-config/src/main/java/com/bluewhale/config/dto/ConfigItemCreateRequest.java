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
    @NotBlank(message = "配置键不能为空")
    @Size(max = 128, message = "配置键长度不能超过 128")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$", message = "配置键仅支持字母/数字开头，含 . _ - 的组合")
    private String configKey;

    /** 配置值 */
    @Size(max = 65535, message = "配置值长度不能超过 65535")
    private String configValue;

    /** 值类型：STRING / NUMBER / BOOLEAN / JSON，默认 STRING */
    @Pattern(regexp = "^(?i)(STRING|NUMBER|BOOLEAN|JSON)$", message = "值类型仅支持 STRING / NUMBER / BOOLEAN / JSON")
    @Size(max = 16, message = "值类型长度不能超过 16")
    private String valueType;

    /** 配置说明 */
    @Size(max = 255, message = "配置说明长度不能超过 255")
    private String description;

    /** 状态：1 启用 / 0 停用，默认 1 */
    private Integer status;
}
