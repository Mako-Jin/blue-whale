package com.bluewhale.crm.marketing.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

import java.util.Locale;

/**
 * 触达明细状态。
 *
 * @author BlueWhale
 */
public enum SendItemStatus {

    /** 待发送 */
    PENDING,

    /** 已发送 */
    SENT,

    /** 已跳过（重复 / 退订 / 黑名单） */
    SKIPPED,

    /** 发送失败 */
    FAILED;

    /**
     * 解析明细状态。
     *
     * @param value 文本
     * @return 明细状态
     */
    public static SendItemStatus from(String value) {
        if (value == null || value.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "触达明细状态不能为空");
        }
        for (SendItemStatus status : values()) {
            if (status.name().equals(value.trim().toUpperCase(Locale.ROOT))) {
                return status;
            }
        }
        throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "触达明细状态非法：" + value);
    }
}
