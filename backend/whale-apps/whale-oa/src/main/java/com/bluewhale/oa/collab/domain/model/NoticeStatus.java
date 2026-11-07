package com.bluewhale.oa.collab.domain.model;

import com.bluewhale.oa.common.OaErrorCode;

/**
 * 公告状态。
 *
 * @author BlueWhale
 */
public enum NoticeStatus {

    /** 草稿 */
    DRAFT,

    /** 已发布 */
    PUBLISHED,

    /** 已撤回 */
    WITHDRAWN;

    /**
     * 解析公告状态。
     *
     * @param value 状态字符串
     * @return 公告状态
     */
    public static NoticeStatus of(String value) {
        try {
            return NoticeStatus.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw OaErrorCode.exception(OaErrorCode.NOTICE_INVALID, "公告状态非法：" + value);
        }
    }
}
