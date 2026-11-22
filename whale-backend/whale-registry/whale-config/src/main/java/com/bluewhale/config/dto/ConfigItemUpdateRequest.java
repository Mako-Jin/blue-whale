package com.bluewhale.config.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 配置项更新请求（配置键不可变，仅允许改值 / 类型 / 说明 / 状态）
 *
 * @author BlueWhale
 */
@Data
public class ConfigItemUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 配置值 */
    @Size(max = 65535, message = "{validation.config.config-item.config-value-size}")
    private String configValue;

    /** 值类型：STRING / NUMBER / BOOLEAN / JSON */
    @Pattern(regexp = "^(?i)(STRING|NUMBER|BOOLEAN|JSON)$", message = "{validation.config.config-item.value-type-pattern}")
    @Size(max = 16, message = "{validation.config.config-item.value-type-size}")
    private String valueType;

    /** 配置说明 */
    @Size(max = 255, message = "{validation.config.config-item.description-size}")
    private String description;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;
}
