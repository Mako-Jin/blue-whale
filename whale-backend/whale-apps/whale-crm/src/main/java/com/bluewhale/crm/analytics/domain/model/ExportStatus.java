package com.bluewhale.crm.analytics.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

/**
 * 分析导出任务状态。
 *
 * @author BlueWhale
 */
public enum ExportStatus {

    /** 执行中 */
    RUNNING,

    /** 已完成 */
    COMPLETED,

    /** 失败 */
    FAILED;

    /**
     * 解析导出状态。
     *
     * @param code 状态编码
     * @return 状态枚举
     */
    public static ExportStatus from(String code) {
        if (code == null || code.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.EXPORT_TASK_FAILED, "导出状态不能为空");
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw CrmErrorCode.exception(CrmErrorCode.EXPORT_TASK_FAILED, "导出状态非法：" + code);
        }
    }
}
