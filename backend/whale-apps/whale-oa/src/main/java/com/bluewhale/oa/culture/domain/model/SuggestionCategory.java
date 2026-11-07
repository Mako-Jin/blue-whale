package com.bluewhale.oa.culture.domain.model;

import com.bluewhale.oa.common.OaErrorCode;

/**
 * 建议类别。
 *
 * @author BlueWhale
 */
public enum SuggestionCategory {

    /** 建议 */
    SUGGESTION,

    /** 投诉 */
    COMPLAINT,

    /** 表扬 */
    PRAISE,

    /** 其他 */
    OTHER;

    /**
     * 解析建议类别。
     *
     * @param value 类别字符串
     * @return 建议类别
     */
    public static SuggestionCategory of(String value) {
        try {
            return SuggestionCategory.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw OaErrorCode.exception(OaErrorCode.SUGGESTION_INVALID, "建议类别非法：" + value);
        }
    }
}
