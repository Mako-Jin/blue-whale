package com.bluewhale.oa.culture.domain.model;

import com.bluewhale.oa.common.OaErrorCode;

/**
 * 建议状态。
 *
 * @author BlueWhale
 */
public enum SuggestionStatus {

    /** 待处理 */
    PENDING,

    /** 处理中 */
    PROCESSING,

    /** 已回复 */
    REPLIED,

    /** 已关闭 */
    CLOSED;

    /**
     * 解析建议状态。
     *
     * @param value 状态字符串
     * @return 建议状态
     */
    public static SuggestionStatus of(String value) {
        try {
            return SuggestionStatus.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw OaErrorCode.exception(OaErrorCode.SUGGESTION_INVALID, "建议状态非法：" + value);
        }
    }
}
