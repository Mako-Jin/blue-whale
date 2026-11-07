package com.bluewhale.oa.collab.domain.model;

import com.bluewhale.oa.common.OaErrorCode;

/**
 * 任务来源。
 *
 * @author BlueWhale
 */
public enum TaskSource {

    /** 个人创建 */
    PERSONAL,

    /** 会议行动项 */
    MEETING,

    /** 公告关联 */
    NOTICE;

    /**
     * 解析任务来源。
     *
     * @param value 来源字符串
     * @return 任务来源
     */
    public static TaskSource of(String value) {
        try {
            return TaskSource.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw OaErrorCode.exception(OaErrorCode.TASK_INVALID, "任务来源非法：" + value);
        }
    }
}
