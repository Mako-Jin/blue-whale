package com.bluewhale.crm.marketing.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

import java.util.Locale;

/**
 * 客户培育任务状态。
 *
 * @author BlueWhale
 */
public enum NurtureStatus {

    /** 待执行 */
    OPEN,

    /** 已完成 */
    DONE,

    /** 已取消 */
    CANCELLED;

    /**
     * 解析培育任务状态。
     *
     * @param value 文本
     * @return 培育任务状态
     */
    public static NurtureStatus from(String value) {
        if (value == null || value.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "培育任务状态不能为空");
        }
        for (NurtureStatus status : values()) {
            if (status.name().equals(value.trim().toUpperCase(Locale.ROOT))) {
                return status;
            }
        }
        throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "培育任务状态非法：" + value);
    }
}
