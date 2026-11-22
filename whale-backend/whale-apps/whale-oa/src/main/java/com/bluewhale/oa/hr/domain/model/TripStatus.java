package com.bluewhale.oa.hr.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

/**
 * 出差申请状态。
 *
 * <p>状态机：{@code PENDING → APPROVED / REJECTED / CANCELLED}，终态不可回流。</p>
 *
 * @author BlueWhale
 */
public enum TripStatus {

    /** 待审批 */
    PENDING,

    /** 已通过 */
    APPROVED,

    /** 已驳回 */
    REJECTED,

    /** 已撤销 */
    CANCELLED;

    /**
     * 解析状态文本。
     *
     * @param value 状态文本
     * @return 出差状态
     */
    public static TripStatus of(String value) {
        try {
            return TripStatus.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.oa.hr.business-trip-status-invalid", "出差状态非法");
        }
    }
}
