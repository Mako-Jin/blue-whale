package com.bluewhale.oa.calendar.domain.model;

import com.bluewhale.oa.common.OaErrorCode;

/**
 * 日程可见性。
 *
 * @author BlueWhale
 */
public enum CalendarVisibility {

    /** 私密：仅所有者可见 */
    PRIVATE,

    /** 共享：所有者与共享名单可见 */
    SHARED,

    /** 公开：租户内所有用户可见 */
    PUBLIC;

    /**
     * 解析可见性，空值默认私密。
     *
     * @param value 可见性字符串
     * @return 可见性
     */
    public static CalendarVisibility of(String value) {
        if (value == null || value.isBlank()) {
            return PRIVATE;
        }
        try {
            return CalendarVisibility.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw OaErrorCode.exception(OaErrorCode.CALENDAR_INVALID, "日程可见性非法：" + value);
        }
    }
}
