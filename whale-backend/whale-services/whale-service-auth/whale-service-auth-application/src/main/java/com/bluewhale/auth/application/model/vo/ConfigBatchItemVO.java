package com.bluewhale.auth.application.model.vo;

/**
 * 批量读取配置的结果项。
 *
 * @param userId    用户 ID
 * @param configKey 配置键
 * @param value     生效配置值（JSON）；未命中任何层时为 null
 * @param source    值来源：USER 用户配置 / TENANT 租户默认 / NONE 未配置
 * @author BlueWhale
 */
public record ConfigBatchItemVO(Long userId, String configKey, String value, String source) {

    /** 值来源：用户配置 */
    public static final String SOURCE_USER = "USER";

    /** 值来源：租户默认 */
    public static final String SOURCE_TENANT = "TENANT";

    /** 值来源：未配置 */
    public static final String SOURCE_NONE = "NONE";
}
