package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 批量读取配置的结果项
 *
 * @author BlueWhale
 */
@Data
public class ConfigBatchItemResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long userId;

    /** 配置键 */
    private String configKey;

    /** 生效配置值（JSON）；未命中任何层时为 null */
    private String value;

    /** 值来源：USER 用户配置 / TENANT 租户默认 / NONE 未配置 */
    private String source;
}
