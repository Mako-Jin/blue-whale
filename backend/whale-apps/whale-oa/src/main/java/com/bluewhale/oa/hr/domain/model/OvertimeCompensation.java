package com.bluewhale.oa.hr.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

/**
 * 加班补偿方式。
 *
 * @author BlueWhale
 */
public enum OvertimeCompensation {

    /** 调休（审批通过后按 8 小时 = 1 天调入调休假期额度） */
    COMP_TIME,

    /** 加班费（进入后续薪资月汇总，本期仅登记补偿方式） */
    PAY;

    /**
     * 解析补偿方式文本。
     *
     * @param value 补偿方式文本
     * @return 补偿方式
     */
    public static OvertimeCompensation of(String value) {
        try {
            return OvertimeCompensation.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BizException(ErrorCode.PARAM_ERROR, "加班补偿方式非法");
        }
    }
}
