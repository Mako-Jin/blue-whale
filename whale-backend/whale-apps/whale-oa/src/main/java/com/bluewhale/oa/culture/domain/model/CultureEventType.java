package com.bluewhale.oa.culture.domain.model;

import com.bluewhale.oa.common.OaErrorCode;

/**
 * 文化动态类型。
 *
 * @author BlueWhale
 */
public enum CultureEventType {

    /** 大事记 */
    MILESTONE,

    /** 活动 */
    ACTIVITY,

    /** 动态 */
    NEWS;

    /**
     * 解析文化动态类型。
     *
     * @param value 类型字符串
     * @return 文化动态类型
     */
    public static CultureEventType of(String value) {
        try {
            return CultureEventType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw OaErrorCode.exception(OaErrorCode.CULTURE_INVALID, "文化动态类型非法：" + value);
        }
    }
}
