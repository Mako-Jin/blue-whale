package com.bluewhale.pm.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

import java.util.Locale;

/**
 * 工作项类型：EPIC 史诗 / STORY 需求 / TASK 任务 / BUG 缺陷 / SUBTASK 子任务。
 *
 * @author BlueWhale
 */
public enum WorkItemType {

    /** 史诗 */
    EPIC,

    /** 需求 */
    STORY,

    /** 任务 */
    TASK,

    /** 缺陷 */
    BUG,

    /** 子任务 */
    SUBTASK;

    /**
     * 解析工作项类型，非法值直接拒绝。
     *
     * @param code 类型编码（大小写不敏感）
     * @return 工作项类型
     */
    public static WorkItemType from(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "工作项类型不能为空");
        }
        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "工作项类型仅支持 EPIC / STORY / TASK / BUG / SUBTASK");
        }
    }
}
