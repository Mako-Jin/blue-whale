package com.bluewhale.crm.marketing.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

import java.util.Locale;

/**
 * 批量触达任务状态。
 *
 * <p>状态机 {@code DRAFT → EXECUTING → COMPLETED}；存在失败明细时进入 {@code FAILED}，
 * 允许重试。</p>
 *
 * @author BlueWhale
 */
public enum MarketingSendStatus {

    /** 草稿 */
    DRAFT,

    /** 执行中 */
    EXECUTING,

    /** 已完成 */
    COMPLETED,

    /** 失败 */
    FAILED;

    /**
     * 解析触达任务状态。
     *
     * @param value 文本
     * @return 任务状态
     */
    public static MarketingSendStatus from(String value) {
        if (value == null || value.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "触达任务状态不能为空");
        }
        for (MarketingSendStatus status : values()) {
            if (status.name().equals(value.trim().toUpperCase(Locale.ROOT))) {
                return status;
            }
        }
        throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "触达任务状态非法：" + value);
    }
}
