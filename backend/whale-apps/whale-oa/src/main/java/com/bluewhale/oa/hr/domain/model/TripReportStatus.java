package com.bluewhale.oa.hr.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

/**
 * 出差报告状态。
 *
 * @author BlueWhale
 */
public enum TripReportStatus {

    /** 待提交 */
    PENDING,

    /** 已提交 */
    SUBMITTED;

    /**
     * 解析报告状态文本。
     *
     * @param value 报告状态文本
     * @return 报告状态
     */
    public static TripReportStatus of(String value) {
        try {
            return TripReportStatus.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BizException(ErrorCode.PARAM_ERROR, "出差报告状态非法");
        }
    }
}
