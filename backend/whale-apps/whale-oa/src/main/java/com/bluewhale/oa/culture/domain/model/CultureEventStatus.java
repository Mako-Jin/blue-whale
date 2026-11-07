package com.bluewhale.oa.culture.domain.model;

import com.bluewhale.oa.common.OaErrorCode;

/**
 * 文化动态状态。
 *
 * @author BlueWhale
 */
public enum CultureEventStatus {

    /** 草稿 */
    DRAFT,

    /** 已发布 */
    PUBLISHED,

    /** 已归档 */
    ARCHIVED;

    /**
     * 解析文化动态状态。
     *
     * @param value 状态字符串
     * @return 文化动态状态
     */
    public static CultureEventStatus of(String value) {
        try {
            return CultureEventStatus.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw OaErrorCode.exception(OaErrorCode.CULTURE_INVALID, "文化动态状态非法：" + value);
        }
    }
}
