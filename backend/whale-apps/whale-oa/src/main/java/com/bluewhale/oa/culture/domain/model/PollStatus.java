package com.bluewhale.oa.culture.domain.model;

import com.bluewhale.oa.common.OaErrorCode;

/**
 * 投票状态。
 *
 * @author BlueWhale
 */
public enum PollStatus {

    /** 草稿 */
    DRAFT,

    /** 已发布（进行中） */
    PUBLISHED,

    /** 已关闭 */
    CLOSED;

    /**
     * 解析投票状态。
     *
     * @param value 状态字符串
     * @return 投票状态
     */
    public static PollStatus of(String value) {
        try {
            return PollStatus.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw OaErrorCode.exception(OaErrorCode.POLL_INVALID, "投票状态非法：" + value);
        }
    }
}
