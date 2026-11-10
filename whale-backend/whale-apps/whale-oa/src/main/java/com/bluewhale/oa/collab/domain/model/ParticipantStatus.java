package com.bluewhale.oa.collab.domain.model;

import com.bluewhale.oa.common.OaErrorCode;

/**
 * 会议参会状态。
 *
 * @author BlueWhale
 */
public enum ParticipantStatus {

    /** 已邀请 */
    INVITED,

    /** 已出席 */
    ATTENDED,

    /** 缺席 */
    ABSENT;

    /**
     * 解析参会状态。
     *
     * @param value 状态字符串
     * @return 参会状态
     */
    public static ParticipantStatus of(String value) {
        try {
            return ParticipantStatus.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw OaErrorCode.exception(OaErrorCode.COLLAB_INVALID, "参会状态非法：" + value);
        }
    }
}
