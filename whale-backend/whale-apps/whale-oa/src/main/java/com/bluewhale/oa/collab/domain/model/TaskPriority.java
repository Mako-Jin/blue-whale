package com.bluewhale.oa.collab.domain.model;

import com.bluewhale.oa.common.OaErrorCode;

/**
 * 任务优先级。
 *
 * @author BlueWhale
 */
public enum TaskPriority {

    /** 高 */
    HIGH,

    /** 普通 */
    NORMAL,

    /** 低 */
    LOW;

    /**
     * 解析任务优先级。
     *
     * @param value 优先级字符串
     * @return 任务优先级
     */
    public static TaskPriority of(String value) {
        try {
            return TaskPriority.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw OaErrorCode.exception(OaErrorCode.TASK_INVALID, "任务优先级非法：" + value);
        }
    }
}
