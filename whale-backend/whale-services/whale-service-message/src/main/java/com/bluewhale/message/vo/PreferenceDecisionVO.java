package com.bluewhale.message.vo;

import java.util.List;

/**
 * 通知偏好解析结果项。
 *
 * @param userId     用户 ID
 * @param channels   生效可投渠道集（站内信永远保留；被偏好/静默/阈值剔光的用户为空列表）
 * @param quiet      是否处于该用户的静默窗口（调用方仅用于展示；渠道集已按静默裁决）
 * @param suppressed 是否全部非站内信渠道被过滤（站内信仍可能单独投递）
 * @param source     偏好来源：USER 用户配置 / TENANT 租户默认 / NONE 未配置（按默认放行）
 * @author BlueWhale
 */
public record PreferenceDecisionVO(Long userId, List<String> channels, boolean quiet,
                                   boolean suppressed, String source) {

    /** 值来源：用户配置 */
    public static final String SOURCE_USER = "USER";

    /** 值来源：租户默认 */
    public static final String SOURCE_TENANT = "TENANT";

    /** 值来源：未配置（按调用方默认放行） */
    public static final String SOURCE_NONE = "NONE";
}
