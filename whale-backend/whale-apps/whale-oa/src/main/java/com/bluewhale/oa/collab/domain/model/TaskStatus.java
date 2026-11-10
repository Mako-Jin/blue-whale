package com.bluewhale.oa.collab.domain.model;

import com.bluewhale.oa.common.OaErrorCode;

/**
 * 个人任务状态。
 *
 * @author BlueWhale
 */
public enum TaskStatus {

    /** 待办 */
    TODO,

    /** 进行中 */
    DOING,

    /** 已完成 */
    DONE,

    /** 已取消 */
    CANCELLED;

    /**
     * 解析任务状态。
     *
     * @param value 状态字符串
     * @return 任务状态
     */
    public static TaskStatus of(String value) {
        try {
            return TaskStatus.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw OaErrorCode.exception(OaErrorCode.TASK_INVALID, "任务状态非法：" + value);
        }
    }
}
