package com.bluewhale.oa.collab.domain.model;

import com.bluewhale.oa.common.OaErrorCode;

/**
 * 会议状态。
 *
 * @author BlueWhale
 */
public enum MeetingStatus {

    /** 已安排 */
    SCHEDULED,

    /** 已结束 */
    FINISHED,

    /** 已取消 */
    CANCELLED;

    /**
     * 解析会议状态。
     *
     * @param value 状态字符串
     * @return 会议状态
     */
    public static MeetingStatus of(String value) {
        try {
            return MeetingStatus.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw OaErrorCode.exception(OaErrorCode.COLLAB_INVALID, "会议状态非法：" + value);
        }
    }
}
