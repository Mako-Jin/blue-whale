package com.bluewhale.oa.expense.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

/**
 * 报销单 ERP 应付推送状态。
 *
 * <p>{@code NONE → PENDING → SENT / FAILED}，失败可重试回到 PENDING。</p>
 *
 * @author BlueWhale
 */
public enum ErpPushStatus {

    /** 未推送 */
    NONE,

    /** 待推送 */
    PENDING,

    /** 已推送 */
    SENT,

    /** 推送失败 */
    FAILED;

    /**
     * 解析状态文本。
     *
     * @param value 状态文本
     * @return 推送状态
     */
    public static ErpPushStatus of(String value) {
        try {
            return ErpPushStatus.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BizException(ErrorCode.PARAM_ERROR, "ERP 推送状态非法");
        }
    }
}
