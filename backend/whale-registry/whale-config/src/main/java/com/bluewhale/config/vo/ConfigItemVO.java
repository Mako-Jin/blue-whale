package com.bluewhale.config.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 配置项视图对象（字段与 {@code config_item} 表一致）
 *
 * @author BlueWhale
 */
@Data
public class ConfigItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 配置键 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 值类型：STRING / NUMBER / BOOLEAN / JSON */
    private String valueType;

    /** 配置说明 */
    private String description;

    /** 当前版本号 */
    private Integer version;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
