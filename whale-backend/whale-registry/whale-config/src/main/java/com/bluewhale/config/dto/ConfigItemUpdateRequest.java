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
    @Size(max = 65535, message = "配置值长度不能超过 65535")
    private String configValue;

    /** 值类型：STRING / NUMBER / BOOLEAN / JSON */
    @Pattern(regexp = "^(?i)(STRING|NUMBER|BOOLEAN|JSON)$", message = "值类型仅支持 STRING / NUMBER / BOOLEAN / JSON")
    @Size(max = 16, message = "值类型长度不能超过 16")
    private String valueType;

    /** 配置说明 */
    @Size(max = 255, message = "配置说明长度不能超过 255")
    private String description;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;
}
